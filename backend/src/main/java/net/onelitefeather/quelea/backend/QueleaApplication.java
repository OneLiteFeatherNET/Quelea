package net.onelitefeather.quelea.backend;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(title = "Quelea", version = "v1"))
public class QueleaApplication {

    public static void main(String[] args) {
        Micronaut.run(QueleaApplication.class, args);
    }
}
