package no.rutebanken.marduk.geocoder.services;

import com.google.cloud.storage.Storage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.sosi.SosiElementWrapperFactory;
import no.rutebanken.marduk.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminUnitRepositoryBuilder {
    @Value("${marduk.admin.units.cache.max.size:30000}")
    private Integer cacheMaxSize;

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${pelias.download.directory:files/adminUnitCache}")
    private String localWorkingDirectory;

    @Autowired
    BlobStoreRepository repository;

    @Autowired
    Storage storage;

    @Value("${blobstore.gcs.container.name}")
    String containerName;

    @Autowired
    private SosiElementWrapperFactory sosiElementWrapperFactory;

    @PostConstruct
    public void init() {
        repository.setStorage(storage);
        repository.setContainerName(containerName);
    }

    public AdminUnitRepository build() {
        RefreshCache refreshJob = new RefreshCache();
        refreshJob.buildNewCache();
        return new CacheAdminUnitRepository(refreshJob.tmpCache, refreshJob.localities);
    }

    private class CacheAdminUnitRepository implements AdminUnitRepository {

        private Cache<String, String> idCache;

        private List<TopographicPlaceAdapter> localities;

        public CacheAdminUnitRepository(Cache<String, String> idCache, List<TopographicPlaceAdapter> localities) {
            this.idCache = idCache;
            this.localities = localities;
        }

        @Override
        public String getAdminUnitName(String id) {
            return idCache.getIfPresent(id);
        }

        @Override
        public TopographicPlaceAdapter getLocality(Point point) {
            if (localities == null) {
                return null;
            }
            for (TopographicPlaceAdapter locality : localities) {
                Geometry geometry = locality.getDefaultGeometry();

                if (geometry != null && geometry.covers(point)) {
                    return locality;
                }
            }
            return null;
        }
    }


    private class RefreshCache {

        private Cache<String, String> tmpCache;

        private List<TopographicPlaceAdapter> localities;

        public void buildNewCache() {
            BlobStoreFiles blobs = repository.listBlobs(blobStoreSubdirectoryForKartverket + "/administrativeUnits");

            localities = new ArrayList<>();
            tmpCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();

            for (BlobStoreFiles.File blob : blobs.getFiles()) {
                if (blob.getName().endsWith(".zip")) {
                    ZipFileUtils.unzipFile(repository.getBlob(blob.getName()), localWorkingDirectory);
                } else if (blob.getName().endsWith(".sos")) {
                    try {
                        FileUtils.copyInputStreamToFile(repository.getBlob(blob.getName()), new File(localWorkingDirectory + blobStoreSubdirectoryForKartverket));
                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to download admin units file: " + ioe.getMessage(), ioe);
                    }
                }
            }
            FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"sos"}, true).stream().forEach(f -> new SosiTopographicPlaceAdapterReader(sosiElementWrapperFactory, f).read().forEach(au -> addAdminUnit(au)));
            new File(localWorkingDirectory).delete();
        }

        private void addAdminUnit(TopographicPlaceAdapter wrapper) {
            if (wrapper.getType() == TopographicPlaceAdapter.Type.LOCALITY) {
                localities.add(wrapper);
            }
            tmpCache.put(wrapper.getId(), wrapper.getName());
        }
    }
}
