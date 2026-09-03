package io.vaullet.template.customer.service;

import io.vaullet.template.customer.dao.CustomerEntity;

/**
 * Entity to domain-model translation.
 *
 * <p>Written by hand and on purpose. Mapping is a handful of lines here, and a plain static method
 * is debuggable, greppable and adds no annotation processor to the build. Reach for MapStruct when
 * a service has dozens of mappings with nested graphs; reach for a runtime reflective mapper
 * (ModelMapper, Dozer) essentially never — silent field-name matching turns a rename into a
 * production bug rather than a compile error.
 */
final class CustomerMapper {

    private CustomerMapper() {}

    static Customer toModel(CustomerEntity entity) {
        return new Customer(
                entity.getId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
