package com.gisproject.location_score.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "shop_data")
@Getter
@Setter
@NoArgsConstructor
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "category_main")
    private String categoryMain;

    @Column(name = "category_sub")
    private String categorySub;

    @Column(name = "address")
    private String address;

    private Double lat;
    private Double lon;

    //PostGIS 좌표 객체 SRID를 명시
    @JsonIgnore
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point geom;

    // 생성 메서드
    public static Shop createShop(String shopName, String categoryMain, String categorySub, String address, Double lat, Double lon) {
        Shop shop = new Shop();
        shop.setShopName(shopName);
        shop.setCategoryMain(categoryMain);
        shop.setCategorySub(categorySub);
        shop.setAddress(address);
        shop.setLat(lat);
        shop.setLon(lon);
        return shop;
    }
}