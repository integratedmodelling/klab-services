package org.integratedmodelling.klab.api.data;

import java.io.Serializable;
import java.util.StringTokenizer;

/**
 * Serializable version compatible with semantic versioning conventions.
 * 
 * @author Ferd
 *
 */
public class Version implements Comparable<Version>, Serializable {

    private static final long serialVersionUID = -3054349171116917643L;

    /**
     * Version identifier parts separator.
     */
    public static final char SEPARATOR = '.';

    /**
     * Parses given string as version identifier. All missing parts will be initialized to 0 or
     * empty string. Parsing starts from left side of the string.
     *
     * @param str version identifier as string
     * @return version identifier object
     */
    public static Version create(final String str) {
        Version result = new Version();
        result.parseString(str);
        return result;
    }

    private int major;
    private int minor;
    private int build;
    private String modifier;

    /**
     * The default version parses the current version string, so it can be used for comparison with
     * others.
     */
    public Version() {
    }

    /**
     * Initialize from a version string.
     *
     * @param version the version
     */
    public Version(String version) {
        parseString(version);
    }

    private void parseString(final String str) {

        major = 0;
        minor = 0;
        build = 0;
        modifier = "";
        StringTokenizer st = new StringTokenizer(str, "" + SEPARATOR, false);
        // major segment
        if (!st.hasMoreTokens()) {
            return;
        }
        String token = st.nextToken();
        try {
            major = Integer.parseInt(token, 10);
        } catch (NumberFormatException nfe) {
            modifier = token;
            while(st.hasMoreTokens()) {
                modifier += st.nextToken();
            }
            return;
        }
        // minor segment
        if (!st.hasMoreTokens()) {
            return;
        }
        token = st.nextToken();
        try {
            minor = Integer.parseInt(token, 10);
        } catch (NumberFormatException nfe) {
            modifier = token;
            while(st.hasMoreTokens()) {
                modifier += st.nextToken();
            }
            return;
        }
        // build segment
        if (!st.hasMoreTokens()) {
            return;
        }
        token = st.nextToken();
        try {
            build = Integer.parseInt(token, 10);
        } catch (NumberFormatException nfe) {
            modifier = token;
            while(st.hasMoreTokens()) {
                modifier += st.nextToken();
            }
            return;
        }
        // name segment
        if (st.hasMoreTokens()) {
            modifier = st.nextToken();
            while(st.hasMoreTokens()) {
                modifier += st.nextToken();
            }
        }
    }

    /**
     * Creates version identifier object from given parts. No validation performed during object
     * instantiation, all values become parts of version identifier as they are.
     *
     * @param aMajor major version number
     * @param aMinor minor version number
     * @param aBuild build number
     * @param aName build name, <code>null</code> value becomes empty string
     */
    public Version(final int aMajor, final int aMinor, final int aBuild, final String modifier) {
        this.major = aMajor;
        this.minor = aMinor;
        this.build = aBuild;
        this.modifier = (modifier == null) ? "" : modifier;
    }

    /**
     * Gets the builds the.
     *
     * @return build number
     */
    public int getBuild() {
        return build;
    }

    /**
     * Gets the major.
     *
     * @return major version number
     */
    public int getMajor() {
        return major;
    }

    /**
     * Gets the minor.
     *
     * @return minor version number
     */
    public int getMinor() {
        return minor;
    }

    /**
     * Gets the name.
     *
     * @return build name
     */
    public String getModifier() {
        return modifier;
    }

    /**
     * Compares two version identifiers to see if this one is greater than or equal to the argument.
     * <p>
     * A version identifier is considered to be greater than or equal if its major component is
     * greater than the argument major component, or the major components are equal and its minor
     * component is greater than the argument minor component, or the major and minor components are
     * equal and its build component is greater than the argument build component, or all components
     * are equal.
     * </p>
     *
     * @param other the other version identifier
     * @return <code>true</code> if this version identifier is compatible with the given version
     *         identifier, and <code>false</code> otherwise
     */
    public boolean greaterOrEqual(final Version other) {
        if (other == null) {
            return false;
        }
        if (major > other.major) {
            return true;
        }
        if ((major == other.major) && (minor > other.minor)) {
            return true;
        }
        if ((major == other.major) && (minor == other.minor) && (build >= other.build)) {
            return true;
        }
        if ((major == other.major) && (minor == other.minor) && (build == other.build)
                && modifier.equalsIgnoreCase(other.modifier)) {
            return true;
        }
        return false;
    }

    /**
     * Compares two version identifiers for compatibility.
     * <p>
     * A version identifier is considered to be compatible if its major component equals to the
     * argument major component, and its minor component is greater than or equal to the argument
     * minor component. If the minor components are equal, than the build component of the version
     * identifier must be greater than or equal to the build component of the argument identifier.
     * </p>
     *
     * @param other the other version identifier
     * @return <code>true</code> if this version identifier is compatible with the given version
     *         identifier, and <code>false</code> otherwise
     */
    public boolean compatible(final Version other) {
        if (other == null) {
            return false;
        }
        if (major != other.major) {
            return false;
        }
        if (minor > other.minor) {
            return true;
        }
        if (minor < other.minor) {
            return false;
        }
        if (build >= other.build) {
            return true;
        }
        return false;
    }

    /**
     * Compares two version identifiers for equivalence.
     * <p>
     * Two version identifiers are considered to be equivalent if their major and minor components
     * equal and are at least at the same build level as the argument.
     * </p>
     *
     * @param other the other version identifier
     * @return <code>true</code> if this version identifier is equivalent to the given version
     *         identifier, and <code>false</code> otherwise
     */
    public boolean equivalent(final Version other) {
        if (other == null) {
            return false;
        }
        if (major != other.major) {
            return false;
        }
        if (minor != other.minor) {
            return false;
        }
        if (build >= other.build) {
            return true;
        }
        return false;
    }

    /**
     * Compares two version identifiers for order using multi-decimal comparison.
     *
     * @param other the other version identifier
     * @return <code>true</code> if this version identifier is greater than the given version
     *         identifier, and <code>false</code> otherwise
     */
    public boolean greater(final Version other) {
        if (other == null) {
            return false;
        }
        if (major > other.major) {
            return true;
        }
        if (major < other.major) {
            return false;
        }
        if (minor > other.minor) {
            return true;
        }
        if (minor < other.minor) {
            return false;
        }
        if (build > other.build) {
            return true;
        }
        return false;

    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Version)) {
            return false;
        }
        Version other = (Version) obj;
        if ((major != other.major) || (minor != other.minor) || (build != other.build)
                || !modifier.equalsIgnoreCase(other.modifier)) {
            return false;
        }
        return true;
    }

    /**
     * Returns the string representation of this version identifier. The result satisfies
     * <code>version.equals(new Version(version.toString()))</code>.
     */
    @Override
    public String toString() {
        String ret = "" + major + SEPARATOR + minor + SEPARATOR + build;
        if (!modifier.isEmpty()) {
            ret += "-" + modifier;
        }
        return ret;
    }

    @Override
    public int compareTo(final Version obj) {
        if (equals(obj)) {
            return 0;
        }
        if (major != obj.major) {
            return major - obj.major;
        }
        if (minor != obj.minor) {
            return minor - obj.minor;
        }
        if (build != obj.build) {
            return build - obj.build;
        }
        return modifier.toLowerCase().compareTo(obj.modifier.toLowerCase());
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }

    public void setBuild(int build) {
        this.build = build;
    }

    public void setModifier(String name) {
        this.modifier = name;
    }

}
