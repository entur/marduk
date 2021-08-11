CREATE TABLE IF NOT EXISTS CAMEL_UNIQUE_FILENAME_AND_DIGEST (processorName VARCHAR(255), digest VARCHAR(255), fileName varchar(255),createdAt TIMESTAMP, primary key (processorname, filename));
CREATE UNIQUE INDEX IF NOT EXISTS  camel_unique_filename_and_digest_processorname_digest_uindex ON CAMEL_UNIQUE_FILENAME_AND_DIGEST (processorName,digest);
