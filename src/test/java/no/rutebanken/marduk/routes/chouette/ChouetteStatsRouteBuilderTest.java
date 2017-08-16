package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class ChouetteStatsRouteBuilderTest {

    @Test
    public void testProviderMatchingLevelFilterAndProviderId() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level1", Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), "level2", Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", Arrays.asList("1", "2")));
    }


    @Test
    public void testProviderMatchingLevelFilter() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level1", new ArrayList<>()));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "level2", null));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), "all", null));
    }

    @Test
    public void testProviderMatchingProviderId() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), null, Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", Arrays.asList("1", "2")));
    }

    @Test
    public void testProviderMatchingWhenNoFiltering() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), null, null));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", new ArrayList<>()));
    }

    @Test
    public void testProviderNotMatchingWhenWrongLevelFiltering() {
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level2", null));
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "level1", Arrays.asList("1", "2")));
    }

    @Test
    public void testProviderNotMatchingNotInProviderIdList() {
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 4l), "all", Arrays.asList("2")));
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(3l, null), "level2", Arrays.asList("1", "2")));
    }


    Provider provider(Long id, Long migrateDataToProviderId) {
        Provider provider = new Provider();
        provider.id = id;
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.migrateDataToProvider = migrateDataToProviderId;
        return provider;
    }
}
