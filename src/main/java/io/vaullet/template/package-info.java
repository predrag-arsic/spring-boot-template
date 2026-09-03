/**
 * Root package of the service.
 *
 * <p>{@link org.jspecify.annotations.NullMarked} makes every type in this package and all
 * sub-packages null-<em>hostile</em> by default: references are non-null unless explicitly marked
 * {@code @Nullable}. Spring Framework 7 ships JSpecify annotations throughout its own API, so IDEs
 * and NullAway/Error Prone can flag a possible {@code NullPointerException} at compile time instead
 * of at 3am. Kotlin callers get the same information as real Kotlin nullability.
 */
@NullMarked
package io.vaullet.template;

import org.jspecify.annotations.NullMarked;
