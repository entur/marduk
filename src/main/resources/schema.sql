CREATE TABLE IF NOT EXISTS camel_unique_filename_and_digest (
     processorname character varying(255) NOT NULL,
     digest character varying(255) NOT NULL,
     filename character varying(255) NOT NULL,
     createdat timestamp without time zone,
     constraint camel_unique_filename_and_digest_pk primary key (processorname, filename)
);
CREATE UNIQUE INDEX IF NOT EXISTS camel_unique_filename_and_digest_processorname_digest_uindex ON camel_unique_filename_and_digest (processorname, digest);
