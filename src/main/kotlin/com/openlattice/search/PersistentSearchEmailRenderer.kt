package com.openlattice.search

import com.google.common.collect.SetMultimap
import com.openlattice.mail.RenderableEmailRequest
import com.openlattice.search.renderers.AlprAlertEmailRenderer
import com.openlattice.search.requests.PersistentSearch
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(PersistentSearchEmailRenderer::class.java)

class PersistentSearchEmailRenderer {

    companion object {

        fun renderEmail(persistentSearch: PersistentSearch, entity: SetMultimap<FullQualifiedName, Any>, userEmail: String): RenderableEmailRequest? {
            var email: RenderableEmailRequest? = null

            when (persistentSearch.type) {
                PersistentSearchNotificationType.ALPR_ALERT -> email = AlprAlertEmailRenderer.renderEmail(persistentSearch, entity, userEmail)

                else -> {
                    logger.error("Unable to render email for type {}", persistentSearch.type)
                }
            }

            return email
        }
    }
}
