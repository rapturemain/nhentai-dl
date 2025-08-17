package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.lifeutils.nhentaidl.model.CURRENT_VERSION
import org.lifeutils.nhentaidl.model.Metadata
import java.io.File

class MigrationServiceCurrent(
    private val objectMapper: ObjectMapper
) : MigrationService {
    override suspend fun migrate(file: File, outputFile: File): Result<Unit> {
        throw NotImplementedError("SHOULD NOT BE CALLED")
    }

    override suspend fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean {
        try {
            val meta = objectMapper.treeToValue(metadataJsonTreeRoot, Metadata::class.java)
            return meta.appMetadata.metadataVersion == CURRENT_VERSION
        } catch (_: Exception) {
            return false
        }
    }
}
