

/*
 * Copyright (C) 2018. OpenLattice, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the owner of the copyright at support@openlattice.com
 *
 */

package com.openlattice.authorization;

import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.hazelcast.util.Preconditions;
import com.openlattice.datastore.services.EdmManager;
import com.openlattice.edm.EntitySet;
import com.openlattice.edm.type.EntityType;
import com.openlattice.edm.type.PropertyType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EdmAuthorizationHelper implements AuthorizingComponent {
    public static final EnumSet<Permission> WRITE_PERMISSION = EnumSet.of( Permission.WRITE );
    public static final EnumSet<Permission> READ_PERMISSION = EnumSet.of( Permission.READ );

    private final EdmManager           edm;
    private final AuthorizationManager authz;

    public EdmAuthorizationHelper( EdmManager edm, AuthorizationManager authz ) {
        this.edm = Preconditions.checkNotNull( edm );
        this.authz = Preconditions.checkNotNull( authz );
    }

    public Map<UUID, PropertyType> getAuthorizedPropertyTypes(
            UUID entitySetId,
            EnumSet<Permission> requiredPermissions ) {
        return getAuthorizedPropertyTypes( entitySetId,
                requiredPermissions,
                edm.getPropertyTypesForEntitySet( entitySetId ),
                Principals.getCurrentPrincipals() );
    }

    public Map<UUID, PropertyType> getAuthorizedPropertyTypes(
            UUID entitySetId,
            EnumSet<Permission> requiredPermissions,
            Map<UUID, PropertyType> propertyTypes,
            Set<Principal> principals ) {

        Map<AclKey, EnumSet<Permission>> accessRequest = propertyTypes.keySet().stream()
                .map( ptId -> new AclKey( entitySetId, ptId ) )
                .collect( Collectors.toMap( Function.identity(), aclKey -> requiredPermissions ) );

        Map<AclKey, EnumMap<Permission, Boolean>> authorizations = authorize( accessRequest, principals );
        authorizations.entrySet().stream()
                .filter( authz -> authz.getValue().values().stream().anyMatch( v -> !v ) )
                .forEach( authz -> propertyTypes.remove( authz.getKey().get( 1 ) ) );

        return propertyTypes;
    }

    /**
     * Note: entitysets are assumed to have same entity type
     */
    public Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertyTypes(
            Set<UUID> entitySetIds,
            Set<UUID> selectedProperties,
            EnumSet<Permission> requiredPermissions ) {
        return getAuthorizedPropertyTypes(
                entitySetIds, selectedProperties, requiredPermissions, Principals.getCurrentPrincipals() );
    }

    /**
     * Note: entitysets are assumed to have same entity type
     */
    private Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertyTypes(
            Set<UUID> entitySetIds,
            Set<UUID> selectedProperties,
            EnumSet<Permission> requiredPermissions,
            Set<Principal> principals ) {
        return entitySetIds.stream()
                .collect( Collectors.toMap(
                        it -> it,
                        it -> getAuthorizedPropertyTypes(
                                it,
                                requiredPermissions,
                                edm.getPropertyTypesAsMap( selectedProperties ),
                                principals ) ) );
    }

    public Set<UUID> getAuthorizedPropertiesOnEntitySet(
            UUID entitySetId,
            EnumSet<Permission> requiredPermissions ) {
        return getAuthorizedPropertiesOnEntitySet(
                entitySetId,
                getAllPropertiesOnEntitySet( entitySetId ),
                requiredPermissions );
    }

    /**
     * @see EdmAuthorizationHelper#getAuthorizedPropertiesByNormalEntitySets(EntitySet, EnumSet, Set)
     */
    public Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertiesByNormalEntitySets(
            EntitySet linkingEntitySet,
            EnumSet<Permission> requiredPermissions ) {
        return getAuthorizedPropertiesByNormalEntitySets(
                linkingEntitySet, requiredPermissions, Principals.getCurrentPrincipals() );
    }

    /**
     * Returns authorized property types for a linking entity set, which is the intersection of authorized properties on
     * each its normal entity sets.
     *
     * @param linkingEntitySet    the linking entity set to check for
     * @param requiredPermissions the permissions to check for
     * @param principals          the principals to check against
     * @return Map of authorized property types by normal entity sets
     */
    public Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertiesByNormalEntitySets(
            EntitySet linkingEntitySet,
            EnumSet<Permission> requiredPermissions,
            Set<Principal> principals ) {
        if ( linkingEntitySet.getLinkedEntitySets().isEmpty() ) {
            return Maps.newHashMap();
        }

        final var propertyPermissions = getPermissionsOnLinkingEntitySetProperties(
                linkingEntitySet.getLinkedEntitySets(), principals );

        final var authorizedProperties = propertyPermissions.entrySet().stream()
                .filter( entry -> entry.getValue().containsAll( requiredPermissions ) )
                .collect( Collectors.toMap(
                        entry -> entry.getKey().getId(),
                        Map.Entry::getKey
                ) );

        return linkingEntitySet.getLinkedEntitySets().stream().collect( Collectors.toMap(
                Function.identity(),
                esId -> authorizedProperties
        ) );

    }

    /**
     * Note: entitysets are assumed to have same entity type
     */
    public Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertyTypesByNormalEntitySet(
            EntitySet linkingEntitySet,
            Set<UUID> selectedProperties,
            EnumSet<Permission> requiredPermissions ) {
        if ( linkingEntitySet.getLinkedEntitySets().isEmpty() ) {
            return Maps.newHashMap();
        }

        final var propertyPermissions = getPermissionsOnLinkingEntitySetProperties(
                linkingEntitySet.getLinkedEntitySets(), selectedProperties, Principals.getCurrentPrincipals() );

        final var authorizedProperties = propertyPermissions.entrySet().stream()
                .filter( entry -> entry.getValue().containsAll( requiredPermissions ) )
                .collect( Collectors.toMap(
                        entry -> entry.getKey().getId(),
                        Map.Entry::getKey
                ) );

        return linkingEntitySet.getLinkedEntitySets().stream().collect( Collectors.toMap(
                Function.identity(),
                esId -> authorizedProperties
        ) );
    }

    public Map<UUID, PropertyType> getAuthorizedPropertyTypesOfLinkingEntitySet(
            EntitySet linkingEntitySet,
            EnumSet<Permission> requiredPermissions ) {
        return getAuthorizedPropertyTypesOfLinkingEntitySet(
                linkingEntitySet, requiredPermissions, Principals.getCurrentPrincipals() );
    }

    public Map<UUID, PropertyType> getAuthorizedPropertyTypesOfLinkingEntitySet(
            EntitySet linkingEntitySet,
            EnumSet<Permission> requiredPermissions,
            Set<Principal> principals ) {
        if ( linkingEntitySet.getLinkedEntitySets().isEmpty() ) {
            return Maps.newHashMap();
        }

        final var propertyPermissions = getPermissionsOnLinkingEntitySetProperties(
                linkingEntitySet.getLinkedEntitySets(), principals );
        return propertyPermissions.entrySet().stream()
                .filter( entry -> entry.getValue().containsAll( requiredPermissions ) )
                .collect( Collectors.toMap(
                        entry -> entry.getKey().getId(),
                        Map.Entry::getKey
                ) );
    }

    /**
     * Note: entity sets of ids provided are assumed to have same entity type
     *
     * @return the intersection of permissions for each property type id of the normal entity sets
     */
    private Map<PropertyType, EnumSet<Permission>> getPermissionsOnLinkingEntitySetProperties(
            Set<UUID> entitySetIds, Set<Principal> principals ) {

        final var propertyTypeIds = getAllPropertiesOnEntitySet( entitySetIds.iterator().next() );
        return getPermissionsOnLinkingEntitySetProperties( entitySetIds, propertyTypeIds, principals );
    }

    /**
     * @return the intersection of permissions for each provided property type id of the normal entity sets
     */
    private Map<PropertyType, EnumSet<Permission>> getPermissionsOnLinkingEntitySetProperties(
            Set<UUID> entitySetIds, Set<UUID> selectedProperties, Set<Principal> principals ) {
        final var propertyTypes = edm.getPropertyTypesAsMap( selectedProperties );

        final var aclKeySets = propertyTypes.keySet().stream().collect( Collectors.toMap(
                Function.identity(),
                ptId -> entitySetIds.stream().map( esId -> new AclKey( esId, ptId ) ).collect( Collectors.toSet() ) ) );

        final var permissionsMap = authz.getSecurableObjectSetsPermissions( aclKeySets.values(), principals );

        return propertyTypes.values().stream().collect( Collectors.toMap( Function.identity(),
                pt -> permissionsMap.get( aclKeySets.get( pt.getId() ) ) ) );
    }

    /**
     * Returns authorized property types for entity sets.
     * Note: entity sets are assumed to have same entity type
     *
     * @param entitySetIds        the ids of entity sets to check for
     * @param requiredPermissions the permissions to check for
     * @param principals          the principals to check against
     * @return Map of authorized property types by entity set ids
     */
    public Map<UUID, Map<UUID, PropertyType>> getAuthorizedPropertiesOnEntitySets(
            Set<UUID> entitySetIds,
            EnumSet<Permission> requiredPermissions,
            Set<Principal> principals ) {
        return ( entitySetIds.isEmpty() )
                ? Maps.newHashMap()
                : getAuthorizedPropertyTypes(
                entitySetIds,
                getAllPropertiesOnEntitySet( entitySetIds.iterator().next() ),
                requiredPermissions,
                principals );
    }

    private Set<UUID> getAuthorizedPropertiesOnEntitySet(
            UUID entitySetId,
            Set<UUID> selectedProperties,
            EnumSet<Permission> requiredPermissions ) {
        return authz.accessChecksForPrincipals( selectedProperties.stream()
                .map( ptId -> new AccessCheck( new AclKey( entitySetId, ptId ), requiredPermissions ) ).collect(
                        Collectors.toSet() ), Principals.getCurrentPrincipals() )
                .filter( authorization -> authorization.getPermissions().values().stream().allMatch( val -> val ) )
                .map( authorization -> authorization.getAclKey().get( 1 ) ).collect( Collectors.toSet() );
    }

    /**
     * Get all property types of an entity set
     * @param entitySetId the id of the entity set
     * @return all the property type ids on the entity type of the entity set
     */
    public Set<UUID> getAllPropertiesOnEntitySet( UUID entitySetId ) {
        EntitySet es = edm.getEntitySet( entitySetId );
        EntityType et = edm.getEntityType( es.getEntityTypeId() );
        return et.getProperties();
    }

    @Override public AuthorizationManager getAuthorizationManager() {
        return authz;
    }

    public static Map<AclKey, EnumSet<Permission>> aclKeysForAccessCheck(
            SetMultimap<UUID, UUID> rawAclKeys,
            EnumSet<Permission> requiredPermission ) {
        return rawAclKeys.entries().stream()
                .collect( Collectors.toMap( e -> new AclKey( e.getKey(), e.getValue() ), e -> requiredPermission ) );
    }
}
