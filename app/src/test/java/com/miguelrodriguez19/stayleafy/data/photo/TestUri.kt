package android.net

import android.os.Parcel
import java.net.URI as JavaUri

/** Small JVM-only Uri value for tests that do not need Android's Uri parser. */
class TestUri(private val value: String) : Uri() {
    private val parsed = JavaUri(value)

    override fun buildUpon(): Builder = error("buildUpon is not used in JVM tests")
    override fun getAuthority(): String? = parsed.authority
    override fun getEncodedAuthority(): String? = parsed.rawAuthority
    override fun getEncodedFragment(): String? = parsed.rawFragment
    override fun getEncodedPath(): String? = parsed.rawPath
    override fun getEncodedQuery(): String? = parsed.rawQuery
    override fun getEncodedSchemeSpecificPart(): String = parsed.rawSchemeSpecificPart
    override fun getEncodedUserInfo(): String? = parsed.rawUserInfo
    override fun getFragment(): String? = parsed.fragment
    override fun getHost(): String? = parsed.host
    override fun getLastPathSegment(): String? = parsed.path?.substringAfterLast('/')
    override fun getPath(): String? = parsed.path
    override fun getPathSegments(): MutableList<String> =
        parsed.path.orEmpty().split('/').filter(String::isNotEmpty).toMutableList()
    override fun getPort(): Int = parsed.port
    override fun getQuery(): String? = parsed.query
    override fun getScheme(): String? = parsed.scheme
    override fun getSchemeSpecificPart(): String = parsed.schemeSpecificPart
    override fun getUserInfo(): String? = parsed.userInfo
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = parsed.scheme == null
    override fun toString(): String = value
    override fun describeContents(): Int = 0
    override fun writeToParcel(destination: Parcel, flags: Int) =
        error("Parceling is not used in JVM tests")

    override fun equals(other: Any?): Boolean = other is TestUri && value == other.value
    override fun hashCode(): Int = value.hashCode()
}
