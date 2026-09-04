package com.petlee.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for every Pet-Lee repository: it owns the persistence context and the five
 * operations that do not depend on which entity is being stored.
 *
 * <h2>Transaction policy — stated here once and followed everywhere</h2>
 * <ul>
 *   <li>Transactions are demarcated in the <strong>service</strong> layer with
 *       {@link jakarta.transaction.Transactional}. <strong>Never in a repository.</strong></li>
 *   <li>Mutating service methods take the default
 *       {@code REQUIRED}; read-only ones carry
 *       {@code @Transactional(Transactional.TxType.SUPPORTS)}.</li>
 *   <li>Repositories are deliberately transaction-agnostic, so a service can compose several
 *       repository calls into one atomic unit — T-16 needs exactly that when it writes an image
 *       row and clears the previous main-image flag together.</li>
 * </ul>
 *
 * <h2>The container owns the {@code EntityManager}</h2>
 * The persistence unit is JTA (see {@code META-INF/persistence.xml}), so the server injects the
 * {@code EntityManager}, enlists it in the caller's transaction, and closes it. Nothing in this
 * project builds an entity-manager factory, opens or closes an {@code EntityManager}, or begins a
 * transaction by hand — doing so re-creates the {@code JpaUtil}/{@code TxRunner} design that
 * ADR-003 deleted, and is a review blocker. T-05's architectural gate greps the whole source tree
 * for those four calls and must come back empty, in comments as well as in code.
 *
 * <h2>Optimistic locking</h2>
 * {@link jakarta.persistence.OptimisticLockException} is never caught here. T-15 distinguishes it
 * from other failures to answer {@code 409 Conflict} rather than {@code 500}, which it can only do
 * if the exception arrives with its type intact. {@link #save(Object)} and {@link #delete(Object)}
 * therefore flush before returning: a version conflict detected at commit time would surface to the
 * service wrapped in a transaction-manager exception, whereas one detected at flush time is thrown
 * straight out of the repository call, inside the service method that made it.
 *
 * <h2>Writing a subclass</h2>
 * A subclass supplies the entity class through the constructor and its own queries — nothing else.
 * It must be a concrete, non-final class with a public no-argument constructor, because
 * {@code @ApplicationScoped} is a normal scope and the container has to proxy it, and it must
 * <strong>carry {@code @ApplicationScoped} itself</strong>:
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class PetRepository extends AbstractRepository<Pet, Long> {
 *     public PetRepository() {
 *         super(Pet.class);
 *     }
 *     // queries of its own, built from getEntityManager()
 * }
 * }</pre>
 *
 * Repeating the annotation looks redundant, because the scope annotation is {@code @Inherited} and
 * the subclass really does have the scope. Discovery is the part that is not inherited: this WAR is
 * an implicit bean archive (it has no {@code beans.xml}), so the container only registers classes
 * that carry a bean-defining annotation of their own. Leave it off and the deployment fails with
 * {@code WELD-001408: Unsatisfied dependencies} at the first injection point — verified on
 * Payara 6.
 *
 * <p>The annotation on this class is therefore documentation of the hierarchy's scope rather than
 * the thing that registers anything; an abstract class is never a bean. Payara says so at every
 * deployment, and the line is expected rather than a fault to chase:
 * {@code WELD-000167: Class ... AbstractRepository is annotated with @ApplicationScoped but it does
 * not declare an appropriate constructor therefore is not registered as a bean!}
 *
 * @param <T>  the entity type
 * @param <ID> the type of that entity's identifier
 */
@ApplicationScoped
public abstract class AbstractRepository<T, ID> {

    /**
     * Injected, managed and closed by the container. Private on purpose: subclasses reach it
     * through {@link #getEntityManager()}, which keeps the field itself out of reach of any
     * lifecycle call.
     */
    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    private final Class<T> entityClass;

    /**
     * Named after the concrete repository, so a log line says which one produced it.
     * {@code java.util.logging} — part of the JDK, so no dependency (ADR-003).
     */
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected AbstractRepository(Class<T> entityClass) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
    }

    /**
     * @return the container-managed persistence context, for subclasses to build their own
     *         queries with. Do not close it and do not start a transaction on it.
     */
    protected EntityManager getEntityManager() {
        return em;
    }

    protected Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Looks an entity up by primary key.
     *
     * @param id the identifier; a {@code null} id yields an empty result rather than an
     *           {@code IllegalArgumentException}, so a caller holding an unparsed path parameter
     *           does not have to guard first
     * @return the entity, or empty when no row has that id
     */
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(entityClass, id));
    }

    /**
     * @return every row of the entity's table, in no defined order. Built with the criteria API
     *         rather than a JPQL string so the entity name never has to be spelled out.
     */
    public List<T> findAll() {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        query.select(root);
        return em.createQuery(query).getResultList();
    }

    /**
     * Persists the entity when it has no identifier yet, merges it otherwise, then flushes.
     *
     * <p>The identifier is read through the JPA metamodel (see {@link #identifierOf}), so
     * subclasses do not have to hand the base class an id accessor and no provider-specific type
     * is referenced.
     *
     * <p>Must be called inside a transaction — see the class-level transaction policy. Calling it
     * outside one raises {@link jakarta.persistence.TransactionRequiredException}, which is the
     * intended failure: it says the service forgot {@code @Transactional}.
     *
     * @param entity the entity to store
     * @return the managed instance; for a merge this is the copy the persistence context owns, not
     *         the argument, so callers must use the returned reference
     * @throws jakarta.persistence.OptimisticLockException if another transaction has changed the
     *         row since this one read it. Deliberately not caught (T-15 maps it to 409).
     */
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        T managed;
        if (isNew(entity)) {
            em.persist(entity);
            managed = entity;
        } else {
            managed = em.merge(entity);
        }

        // Flush inside the call so a version conflict is thrown here, as an
        // OptimisticLockException, instead of at commit time wrapped by the transaction manager.
        em.flush();

        logger.log(Level.FINE, () -> "Saved " + entityClass.getSimpleName() + " " + identifierOf(managed));
        return managed;
    }

    /**
     * Removes the entity's row. A detached instance is merged first, because
     * {@code EntityManager.remove} only accepts a managed one.
     *
     * <p>Must be called inside a transaction, as {@link #save(Object)} must.
     *
     * @param entity the entity to remove
     * @throws jakarta.persistence.OptimisticLockException if the row changed since it was read.
     *         Deliberately not caught.
     */
    public void delete(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        Object id = identifierOf(entity);
        em.remove(em.contains(entity) ? entity : em.merge(entity));
        em.flush();

        logger.log(Level.FINE, () -> "Deleted " + entityClass.getSimpleName() + " " + id);
    }

    /**
     * @return how many rows the entity's table holds
     */
    public long count() {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        query.select(builder.count(query.from(entityClass)));
        return em.createQuery(query).getSingleResult();
    }

    /**
     * An entity is new when the provider reports no identifier for it. This distinguishes
     * "never persisted" from "persisted with a low id", which is why every id in the model is a
     * wrapper type (ADR-002 #3, #8).
     */
    private boolean isNew(T entity) {
        return identifierOf(entity) == null;
    }

    /**
     * Reads the entity's {@code @Id} value.
     *
     * <p>The obvious route to this is the persistence unit's own utility object, but reaching it
     * means naming a type the T-05 architectural gate greps for, and the gate is worth more than
     * the shortcut. The metamodel is equally JPA-standard: it reports which member carries the id,
     * and that member is read directly. Entities in this project annotate fields, so the member is
     * a {@link Field}; the {@link Method} branch keeps the method-access mapping working too.
     */
    private Object identifierOf(T entity) {
        EntityType<T> type = em.getMetamodel().entity(entityClass);
        Member idMember = type.getId(type.getIdType().getJavaType()).getJavaMember();
        try {
            if (idMember instanceof Method getter) {
                getter.setAccessible(true);
                return getter.invoke(entity);
            }
            Field field = (Field) idMember;
            field.setAccessible(true);
            return field.get(entity);
        } catch (ReflectiveOperationException | ClassCastException e) {
            // Only reachable if the entity's id mapping is malformed, which is a deployment-time
            // defect rather than anything a caller can recover from.
            logger.log(Level.SEVERE, e, () -> "Cannot read the identifier of " + entityClass.getName());
            throw new IllegalStateException("Cannot read the identifier of " + entityClass.getName(), e);
        }
    }
}
