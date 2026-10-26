package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.Area;

public record AreaResponse(Long id, String code, String name, String description) {

    public static AreaResponse from(Area a) {
        return new AreaResponse(a.getId(), a.getCode(), a.getName(), a.getDescription());
    }
}
