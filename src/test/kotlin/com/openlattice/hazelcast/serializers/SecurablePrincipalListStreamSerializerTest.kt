/*
 * Copyright (C) 2019. OpenLattice, Inc.
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
 *
 */
package com.openlattice.hazelcast.serializers

import com.kryptnostic.rhizome.hazelcast.serializers.AbstractStreamSerializerTest
import com.openlattice.authorization.AclKey
import com.openlattice.authorization.SecurablePrincipal
import com.openlattice.mapstores.TestDataFactory
import com.openlattice.organizations.SecurablePrincipalList
import org.apache.commons.lang3.RandomStringUtils
import java.util.*
import kotlin.random.Random

class SecurablePrincipalListStreamSerializerTest
    : AbstractStreamSerializerTest<SecurablePrincipalListStreamSerializer, SecurablePrincipalList>() {

    companion object {
        @JvmStatic
        fun createSecurablePrincipalList(): SecurablePrincipalList {
            return if (Random.nextBoolean()) {
                SecurablePrincipalList(
                        mutableListOf(
                                SecurablePrincipal(
                                        AclKey(UUID.randomUUID()),
                                        TestDataFactory.userPrincipal(),
                                        RandomStringUtils.randomAlphanumeric(10),
                                        Optional.of(RandomStringUtils.randomAlphanumeric(10))
                                ),
                                SecurablePrincipal(
                                        AclKey(UUID.randomUUID()),
                                        TestDataFactory.userPrincipal(),
                                        RandomStringUtils.randomAlphanumeric(10),
                                        Optional.of(RandomStringUtils.randomAlphanumeric(10))
                                )
                        )
                )
            } else {
                SecurablePrincipalList(mutableListOf())
            }
        }
    }

    override fun createSerializer(): SecurablePrincipalListStreamSerializer {
        return SecurablePrincipalListStreamSerializer()
    }

    override fun createInput(): SecurablePrincipalList {
        return createSecurablePrincipalList()
    }
}