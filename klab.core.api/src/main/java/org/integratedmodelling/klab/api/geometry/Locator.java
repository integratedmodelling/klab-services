package org.integratedmodelling.klab.api.geometry;

/**
 * Locators are topological pointers that can be used to locate and subset observations. A locator
 * is a geometry that comes from a geometry that contains it, and maintains the relationship with
 * the geometry that it locates.
 * <p>
 * Any geometry or subset of it can be used as a locator. Scales and extents are also locators and
 * can produce their component locators as appropriate. They can also "relocate" by producing lazy
 * mediators that allow seeing observations through different lenses. When a single extent is used
 * as a locator, it will leave all the extents of any remaining others in the scale: if the scale
 * has multiple states for the locator, one or more dimensions in the located geometry will have
 * multiple states.
 * <p>
 * Numeric offsets are only exposed to communicate with external raw data APIs; within k.LAB code,
 * translation should happen within the implementing classes, and the "conformant" cases where the
 * locator correspond to a simple offset without mediations should be detected and translated as
 * fast as possible. When offsets are needed, the {@link OffsetImpl} locator can be used as the
 * class in a {@link #as(Class)} request. All locators should implement at least a translation to
 * {@link OffsetImpl}.
 * <p>
 * Locators can be parsed from a simple string parameters using the syntax below:
 * 
 * <pre>
 * &lt;geometry&gt;@n,m,...
 * </pre>
 * 
 * where the {@link Geometry geometry} specs before @ and the @ character itself are optional if the
 * locator is part of a request that unambiguously identifies an observation. Any of the numbers in
 * the list after that is either a linear long integer offset in the correspondent dimension of the
 * geometry or a set of dimensional offsets in parentheses for dimensions with inherent
 * dimensionality > 1, and each can be substituted by a dot, meaning that the entire dimension is
 * located. If only one offset is mentioned, the remaining ones are substituted with dots. So
 * usually [0] in a temporally explicit context means whatever is located at t=0.
 * 
 * @author Ferd
 *
 */
public interface Locator {

    /**
     * Use UniversalLocator.INSTANCE instead of null to pass to extent functions when the entire
     * extent should be used.
     */
    public class UniversalLocator implements Locator {

        public static UniversalLocator INSTANCE = new UniversalLocator();

        @Override
        public <T extends Locator> T as(Class<T> cls) {
            return null;
        }

    }

    /**
     * Adapt the locator to another with the needed API. If the parameter is the class or the type
     * of an extent we want to selec the returned locator may only report location information for
     * that extent. For example, geometry.as(Space.class) will return a locator reflecting only the
     * spatial dimension. Such partial locators should not be used for further location.
     * 
     * @param cls
     * @return
     */
    <T extends Locator> T as(Class<T> cls);

}
