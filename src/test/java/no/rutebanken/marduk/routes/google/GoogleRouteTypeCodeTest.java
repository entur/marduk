package no.rutebanken.marduk.routes.google;

import org.junit.Assert;
import org.junit.Test;

public class GoogleRouteTypeCodeTest {


    @Test
    public void supportedVersionsAreReturnedUnchanged() {
        assertMapping(100, 100);
        assertMapping(101, 101);
        assertMapping(200, 200);
        assertMapping(401, 401);
    }


    @Test
    public void unsupportedVersionsWithExplicitMappingsAreMapped() {
        assertMapping(300, 100);
        assertMapping(500, 401);
        assertMapping(600, 402);
        assertMapping(1200, 1000);
        assertMapping(1500, 1501);
    }

    @Test
    public void unsupportedVersionsWithoutExplicitMappingWithSupportedBaseTypeAreReturnedAsBaseType() {
        assertMapping(117, 100);
        assertMapping(118, 100);
        assertMapping(1021, 1000);
        assertMapping(1703, 1700);
    }

    @Test
    public void unsupportedVersionsWithoutExplicitMappingWithUnsupportedBaseTypesWithExplicitMappingAreReturnedAsBaseTypesMapping() {
        assertMapping(1502, 1501);
        assertMapping(1506, 1501);
    }

    @Test
    public void unsupportedVersionsWithNoMappingAreReturnedAsMisc() {
        assertMapping(20000, 1700);
        assertMapping(1600, 1700);
        assertMapping(1601, 1700);
    }

    private void assertMapping(int org, int expected) {
        Assert.assertEquals(expected, GoogleRouteTypeCode.toGoogleSupportedRouteTypeCode(org));
    }
}
