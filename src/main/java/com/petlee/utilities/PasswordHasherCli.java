package com.petlee.utilities;

/**
 * Prints the digest of one password, so T-03's seeded administrator row can carry a genuine
 * {@link PasswordHasher} value instead of a placeholder.
 *
 * <pre>
 * java -cp target/classes com.petlee.utilities.PasswordHasherCli "Admin123!"
 * </pre>
 *
 * <p>The password is passed as an argument, which puts it in the shell history of whoever runs
 * this. That is acceptable for seeding one known demo credential and for nothing else — this class
 * is not part of any request path.
 */
public final class PasswordHasherCli {

    private PasswordHasherCli() {
        // Entry point only.
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: PasswordHasherCli <password>");
            System.exit(2);
            return;
        }
        System.out.println(PasswordHasher.hash(args[0]));
    }
}
