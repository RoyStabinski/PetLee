package com.petlee.web.el;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;

/**
 * Lets the pages read a record's components as properties, so {@code #{pet.name}} works on a
 * {@code PetDTO}. Jakarta EL 5, which Jakarta EE 10 servers ship, reads only JavaBean getters and
 * a record has none; EL 6 (Jakarta EE 11) does this itself. Registered in faces-config.xml.
 */
public class RecordELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        RecordComponent component = componentOf(base, property);
        if (component == null) {
            return null;
        }
        context.setPropertyResolved(base, property);
        try {
            return component.getAccessor().invoke(base);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new jakarta.el.ELException(e);
        }
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        RecordComponent component = componentOf(base, property);
        if (component == null) {
            return null;
        }
        context.setPropertyResolved(base, property);
        return component.getType();
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (componentOf(base, property) != null) {
            throw new PropertyNotWritableException("A record's components are read-only");
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (componentOf(base, property) == null) {
            return false;
        }
        context.setPropertyResolved(base, property);
        return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return base != null && base.getClass().isRecord() ? String.class : null;
    }

    private static RecordComponent componentOf(Object base, Object property) {
        if (base == null || !base.getClass().isRecord() || !(property instanceof String name)) {
            return null;
        }
        for (RecordComponent component : base.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        return null;
    }
}
