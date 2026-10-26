package vn.edu.phenikaa.canteen;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class CanteenApplication {

    public static void main(String[] args) {
        // NFR08: mọi thời điểm theo múi giờ Việt Nam, kể cả khi server đặt UTC.
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.ZONE));
        SpringApplication.run(CanteenApplication.class, args);
    }
}
