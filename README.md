
# Marduk [![CircleCI](https://circleci.com/gh/entur/marduk/tree/master.svg?style=svg)](https://circleci.com/gh/entur/marduk/tree/master)

Marduk orchestrates the timetable data import pipeline.

# Data input channels
Marduk receives datasets from the following channels:
- File upload from the operator portal ([Bel](https://github.com/entur/bel))
- File upload from the administration console ([Ninkasi](https://github.com/entur/ninkasi))
- File transfer via a REST API
- Internal PubSub messaging for flight data ([Extime](https://github.com/entur/extime))

Input data are primarily NeTEx datasets. GTFS datasets are still in use but will ultimately be migrated to NeTEx.

# Data import workflow
Marduk performs basic validation checks on the input files (check that the file is a valid zip archive, simple data format check) and then initiates the import workflow:
1. Pre-validation of NeTEx data ([Antu](https://github.com/entur/antu))  
Antu runs a set of validation rules against the NeTEx dataset (XML Schema validation, XPath queries, ...).
2. Import of NeTEx data into the data provider timetable database ([Chouette](https://github.com/entur/chouette))  
Chouette imports the NeTEx data into an intermediate, work-in-progress database.
3. Validation of the imported data ([Chouette](https://github.com/entur/chouette))  
Chouette runs a second set of validation rules on the intermediate database.
4. Transfer from the data provider database to the central timetable database  ([Chouette](https://github.com/entur/chouette))  
Chouette copies the timetable data from the intermediate database to the central database. The central database contains only validated data. 
5. Validation of the transferred timetable data ([Chouette](https://github.com/entur/chouette))  
Chouette runs the same set of validation rules as in step 3. 
6. NeTEx export ([Chouette](https://github.com/entur/chouette))  
Chouette exports the timetable data into a NeTEx archive.
7. NeTEx export post-validation ([Antu](https://github.com/entur/antu))  
Antu runs the same set of validation rules as in step 1. against the exported NeTEx dataset.
8. Publication of validated NeTEx datasets  
Marduk publishes the validated datasets on [Entur Open Data Portal](https://developer.entur.org/stops-and-timetable-data)
9. Update of the journey planner graph  
Marduk triggers a rebuild of the journey planner graph so that it reflects the latest version of timetable data ([OTP](https://github.com/opentripplanner))

# Periodic timetable data revalidation
Some NeTEx validation rules are time-dependent, in particular those that rely on external reference data such as the [Norwegian Stop Place Register](https://stoppested.entur.org/).
It is necessary to revalidate periodically the imported datasets to guarantee that they still refer to valid stop places. Revalidation allows also for pruning expired data, such as trip  whose effective date is in the past.  
Marduk schedules a nightly revalidation of every dataset which triggers a regeneration of each NeTEx export file. Expired data are removed from the new exports.

# GTFS export
In addition to orchestrating NeTEx data export, Marduk triggers also an export of GTFS data ([Damu](https://github.com/entur/damu)) 

# Integration with flexible transport data
Marduk merges the NeTEx datasets containing flexible timetables generated in NPlan ([Uttu](https://github.com/entur/uttu) and [Enki](https://github.com/entur/enki)) with those generated in Chouette.

# Journey planner street graph update
[OpenTripPlanner](https://github.com/opentripplanner) relies on OpenStreetMap data to calculate the first/last leg of a journey (walk from start point or to destination point).
Marduk schedules a nightly download of OpenStreetMap data that in turn is used by OpenTripPlanner to build an updated street graph.
