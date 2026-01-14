package com.gisproject.location_score.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CsvDto {
        private String shopName;
        private String categoryMain;
        private String categorySub;
        private String address;
        private double lat;
        private double lon;
}