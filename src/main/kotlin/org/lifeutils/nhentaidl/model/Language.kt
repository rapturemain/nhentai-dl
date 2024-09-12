package org.lifeutils.nhentaidl.model

enum class Language(val searchParam: String) {
    ENGLISH("english"),
    JAPANESE("japanese"),
    CHINESE("chinese");

    companion object {
        fun fromString(searchParam: String): Language? {
            return entries.find { it.searchParam.equals(searchParam, ignoreCase = true) }
        }
    }
}
