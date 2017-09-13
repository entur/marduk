package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.tuple.Pair;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.InterchangeWeightingEnumeration;
import org.rutebanken.netex.model.MetroSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.TramSubmodeEnumeration;
import org.rutebanken.netex.model.WaterSubmodeEnumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StopPlaceBoostConfiguration {

    private static final String ALL_TYPES = "*";

    private long defaultValue;

    private Map<StopTypeEnumeration, StopTypeBoostConfig> stopTypeScaleFactorMap = new HashMap<>();

    private Map<InterchangeWeightingEnumeration, Double> interchangeScaleFactorMap = new HashMap<>();

    @Autowired
    public StopPlaceBoostConfiguration(@Value("${pelias.stop.place.boost.config:{\"defaultValue\":1000}}") String boostConfig) {
        init(boostConfig);
    }

    public long getPopularity(List<Pair<StopTypeEnumeration, Enum>> stopTypeAndSubModeList, InterchangeWeightingEnumeration interchangeWeighting) {
        long popularity = defaultValue;

        double stopTypeAndSubModeFactor = stopTypeAndSubModeList.stream().collect(Collectors.summarizingDouble(stopTypeAndSubMode -> getStopTypeAndSubModeFactor(stopTypeAndSubMode.getLeft(), stopTypeAndSubMode.getRight()))).getSum();

        if (stopTypeAndSubModeFactor > 0) {
            popularity *= stopTypeAndSubModeFactor;
        }

        Double interchangeFactor = interchangeScaleFactorMap.get(interchangeWeighting);
        if (interchangeFactor != null) {
            popularity *= interchangeFactor;
        }

        return popularity;
    }

    private double getStopTypeAndSubModeFactor(StopTypeEnumeration stopType, Enum subMode) {
        StopTypeBoostConfig factorsPerSubMode = stopTypeScaleFactorMap.get(stopType);
        if (factorsPerSubMode != null) {
            return factorsPerSubMode.getFactorForSubMode(subMode);
        }
        return 0;
    }


    private void init(String boostConfig) {
        StopPlaceBoostConfigJSON input = fromString(boostConfig);

        defaultValue = input.defaultValue;

        if (input.interchangeFactors != null) {
            input.interchangeFactors.forEach((interchangeTypeString, factor) -> interchangeScaleFactorMap.put(InterchangeWeightingEnumeration.fromValue(interchangeTypeString), factor));
        }

        if (input.stopTypeFactors != null) {
            for (Map.Entry<String, Map<String, Double>> stopTypeConfig : input.stopTypeFactors.entrySet()) {
                StopTypeEnumeration stopType = StopTypeEnumeration.fromValue(stopTypeConfig.getKey());

                Map<String, Double> inputFactorsPerSubMode = stopTypeConfig.getValue();

                StopTypeBoostConfig stopTypeBoostConfig = new StopTypeBoostConfig(inputFactorsPerSubMode.getOrDefault(ALL_TYPES, 1.0));
                stopTypeScaleFactorMap.put(stopType, stopTypeBoostConfig);

                inputFactorsPerSubMode.remove(ALL_TYPES);
                if (inputFactorsPerSubMode != null) {
                    inputFactorsPerSubMode.forEach((subModeString, factor) -> stopTypeBoostConfig.factorPerSubMode.put(toSubModeEnum(stopType, subModeString), factor));
                }
            }
        }
    }


    private StopPlaceBoostConfigJSON fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(string, StopPlaceBoostConfigJSON.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Enum toSubModeEnum(StopTypeEnumeration stopType, String subMode) {
        switch (stopType) {
            case AIRPORT:
                return AirSubmodeEnumeration.fromValue(subMode);
            case HARBOUR_PORT:
            case FERRY_STOP:
            case FERRY_PORT:
                return WaterSubmodeEnumeration.fromValue(subMode);
            case BUS_STATION:
            case COACH_STATION:
            case ONSTREET_BUS:
                return BusSubmodeEnumeration.fromValue(subMode);
            case RAIL_STATION:
                return RailSubmodeEnumeration.fromValue(subMode);
            case METRO_STATION:
                return MetroSubmodeEnumeration.fromValue(subMode);
            case ONSTREET_TRAM:
            case TRAM_STATION:
                return TramSubmodeEnumeration.fromValue(subMode);
        }
        return null;
    }


    private class StopTypeBoostConfig {

        public double defaultFactor = 1;

        public Map<Enum, Double> factorPerSubMode = new HashMap<>();

        public StopTypeBoostConfig(double defaultFactor) {
            this.defaultFactor = defaultFactor;
        }

        public Double getFactorForSubMode(Enum subMode) {
            return factorPerSubMode.getOrDefault(subMode, defaultFactor);
        }
    }


}
