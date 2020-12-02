package com.netflix.graphql.dgs.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import org.slf4j.LoggerFactory

/**
 * Representation of a GraphQL response, which may contain GraphQL errors.
 * This class gives convenient JSON parsing methods to get data out of the response.
 */
data class GraphQLResponse(val json: String) {

    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        private val jsonPathConfig: Configuration = Configuration.defaultConfiguration()
                .jsonProvider(JacksonJsonProvider(mapper))
                .mappingProvider(JacksonMappingProvider(mapper))
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
    }

    /**
     * A JsonPath DocumentContext. Typically only used internally.
     */
    val parsed: DocumentContext = JsonPath.using(jsonPathConfig).parse(json)

    /**
     * Map representation of data
     */
    val data: Map<String, Any> = parsed.read("data")?: emptyMap()
    val errors : List<GraphQLError> = parsed.read("errors", object: TypeRef<List<GraphQLError>>() {})?: emptyList()

    private val logger = LoggerFactory.getLogger(GraphQLResponse::class.java)

    /**
     * Deserialize data into the given class.
     * The class may need Jackson annotations for correct mapping.
     */
    fun <T> dataAsObject(clazz: Class<T>): T {
        return mapper.convertValue(data, clazz)
    }

    /**
     * Extract values given a JsonPath. The return type will be whatever type you expect.
     * Although this looks type safe, it really isn't. Make sure values map to the expected type.
     * For JSON objects, a Map is returned. If you want to deserialize to a class, use #extractValueAsObject instead.
     */
    fun <T> extractValue(path: String): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath)
        } catch (ex: Exception) {
            logger.error("Error extracting path '${path}' from data: '${data}'")
            throw ex
        }
    }

    /**
     * Extract values given a JsonPath and deserialize into the given class.
     */
    fun <T> extractValueAsObject(path: String, clazz: Class<T>): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath, clazz)
        } catch(ex: Exception) {
            logger.error("Error extracting path '${path}' from data: '${data}'")
            throw ex
        }
    }

    /**
     * Extract values given a JsonPath and deserialize into the given TypeRef.
     * Use this for Lists of a specific type.
     */
    fun <T> extractValueAsObject(path: String, typeRef: TypeRef<T>): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath, typeRef)
        } catch(ex: Exception) {
            logger.error("Error extracting path '${path}' from data: '${data}'")
            throw ex
        }
    }

    /**
     * Extracts RequestDetails from the response if available.
     * Returns null otherwise.
     */
    fun getRequestDetails(): RequestDetails {
        return extractValueAsObject("gatewayRequestDetails", RequestDetails::class.java)
    }

    private fun getDataPath(path: String) = if (!path.startsWith("data")) "data.$path" else path

    fun hasErrors(): Boolean = errors.isNotEmpty()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RequestDetails(val requestId: String?, val edgarLink: String?)
