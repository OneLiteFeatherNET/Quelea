package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Singleton;

@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(JsonExceptionHandler.class)
public class JsonSyntaxExceptionHandler extends BadRequestExceptionHandler<JsonSyntaxException> {

    public JsonSyntaxExceptionHandler(ProblemFactory factory) {
        super(factory, "The request body is not valid JSON.");
    }
}
