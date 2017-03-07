package no.rutebanken.marduk.geocoder.services;

import com.google.cloud.storage.Storage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONCollection;
import no.rutebanken.marduk.geocoder.geojson.AbstractKartverketGeojsonWrapper;
import no.rutebanken.marduk.geocoder.geojson.KartverketFeatureWrapperFactory;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@Service
public class AdminUnitRepositoryBuilder {
	@Value("${marduk.admin.units.cache.max.size:30000}")
	private Integer cacheMaxSize;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${pelias.download.directory:files/adminUnitCache}")
	private String localWorkingDirectory;

	@Autowired
	BlobStoreRepository repository;

	@Autowired
	Storage storage;

	@Value("${blobstore.gcs.container.name}")
	String containerName;

	@PostConstruct
	public void init() {
		repository.setStorage(storage);
		repository.setContainerName(containerName);
	}

	public AdminUnitRepository build() {
		return new CacheAdminUnitRepository(new RefreshCache().buildNewCache());
	}

	private class CacheAdminUnitRepository implements AdminUnitRepository {

		private Cache<String, String> cache;

		public CacheAdminUnitRepository(Cache<String, String> cache) {
			this.cache = cache;
		}

		@Override
		public String getAdminUnitName(String id) {
			return cache.getIfPresent(id);
		}
	}


	private class RefreshCache {

		private Cache<String, String> tmpCache;

		public Cache<String, String> buildNewCache() {
			BlobStoreFiles blobs = repository.listBlobs(blobStoreSubdirectoryForKartverket + "/administrativeUnits");

			tmpCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
			for (BlobStoreFiles.File blob : blobs.getFiles()) {
				if (blob.getName().endsWith(".zip")) {
					ZipFileUtils.unzipFile(repository.getBlob(blob.getName()), localWorkingDirectory);

					FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"geojson"}, true).stream().forEach(f -> addAdminUnitsInFile(f));

					new File(localWorkingDirectory).delete();
				}

			}

			return tmpCache;
		}

		private void addAdminUnitsInFile(File file) {
			try {
				new FeatureJSONCollection(new FileInputStream(file))
						.forEach(f -> addAdminUnit(f));
			} catch (FileNotFoundException f) {
				throw new RuntimeException("Exception while processing admin units in geojson file: " + f.getMessage(), f);
			}
		}

		private void addAdminUnit(SimpleFeature feature) {
			AbstractKartverketGeojsonWrapper wrapper = KartverketFeatureWrapperFactory.createWrapper(feature);

			tmpCache.put(wrapper.getId(), wrapper.getName());
		}
	}
}
