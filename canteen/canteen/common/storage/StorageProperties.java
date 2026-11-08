package vn.edu.phenikaa.canteen.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @DefaultValue("./uploads") String dir,
        @DefaultValue("2MB") DataSize maxFileSize) {
}
