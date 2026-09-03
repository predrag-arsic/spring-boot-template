package io.vaullet.template.customer;

/**
 * Lifecycle state of a customer.
 *
 * <p>Deliberately at the root of the feature slice rather than inside {@code dao}, {@code service}
 * or {@code api}: it is vocabulary all three layers share, and putting it in one of them would
 * force the other two to depend on that layer. The layering test in
 * {@code io.vaullet.template.architecture} enforces exactly that boundary, so this is the kind of
 * decision the build catches rather than a reviewer.
 *
 * <p>Persisted by name, never by ordinal — see {@code CustomerEntity}.
 */
public enum CustomerStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
