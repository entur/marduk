insert into chouette_info(prefix, data_space, organisation, user) values ('rutebanken', 'rutebanken', 'Rutebanken', 'admin@rutebanken.org');
insert into chouette_info(prefix, data_space, organisation, user) values ('tds1', 'testDS1', 'Rutebanken', 'admin@rutebanken.org');

insert into provider(id, name, sftp_account, chouette_info_id) values (42, 'Flybussekspressen', 'kartverk', 1);
insert into provider(id, name, sftp_account, chouette_info_id) values (1, 'Rutebanken', 'nvdb', 2);