package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.management.ChouetteInfo;
import no.rutebanken.marduk.management.Provider;
import no.rutebanken.marduk.management.ProviderRepository;
import no.rutebanken.marduk.management.ProviderRepositoryImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.LocalDate;

public class ChouetteExportRouteBuilderTest {

    private ChouetteExportRouteBuilder chouetteExportRouteBuilder;

    @Before
    public void setup() {
        chouetteExportRouteBuilder = new ChouetteExportRouteBuilder() {
            @Override
            protected ProviderRepository getProviderRepository() {
                return new ProviderRepositoryImpl() {
                    @Override
                    public Provider getProviderById(Long id) {
                        return new Provider(2L, "blah", "blah", new ChouetteInfo("tds", "tds", "rutebanken", "user"));
                    }
                };
            }
        };
    }


    @Ignore
    @Test
    public void testNow() throws Exception {
        System.out.println(chouetteExportRouteBuilder.toDate(LocalDate.now()));
    }

    @Ignore
    @Test
    public void testGetJsonFileContent() throws Exception {
        System.out.println(chouetteExportRouteBuilder.getJsonFileContent(2L));
    }

}