package com.Engine.DTO;

public class GateWayRouteDTO {
    private String routeId;
    private String uri;
    private String predicatesJson;
    private String filtersJson;
    private int routeOrder;

    public String getFiltersJson() {
        return filtersJson;
    }

    public String getPredicatesJson() {
        return predicatesJson;
    }

    public String getRouteId() {
        return routeId;
    }

    public int getRouteOrder() {
        return routeOrder;
    }

    public String getUri() {
        return uri;
    }
}
