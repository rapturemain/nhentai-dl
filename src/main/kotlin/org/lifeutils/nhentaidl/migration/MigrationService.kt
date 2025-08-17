package org.lifeutils.nhentaidl.migration

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

interface MigrationService {
    suspend fun migrate(file: File, outputFile: File): Result<Unit>

    suspend fun canMigrate(metadataJsonTreeRoot: JsonNode): Boolean
}
