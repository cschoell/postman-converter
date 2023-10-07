package org.cschoell.bruno.mapper;

import org.apache.commons.lang3.StringUtils;
import org.cschoell.bruno.model.Auth;
import org.cschoell.bruno.model.Query;
import org.cschoell.bruno.model.*;
import org.cschoell.postman.model.Body;
import org.cschoell.postman.model.*;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.cschoell.postman.model.Body.Mode.RAW;

@Mapper()
public interface PostmanToBrunoCollectionMapper {

    PostmanToBrunoCollectionMapper INSTANCE = Mappers.getMapper( PostmanToBrunoCollectionMapper.class );

    @Mapping(target = "requests", source = "item", qualifiedByName = "item")
    BrunoCollection toBrunoCollection(PostmanCollection in);

    @AfterMapping
    @Named("item")
    default List<BrunoRequestFile> afterRequestsMapping(List<PostmanPolymorphicBase> polymorphicBases, @MappingTarget List<BrunoRequestFile> brunoRequestFiles) {
        final List<Folder> folders = polymorphicBases.stream().filter(postmanPolymorphicBase -> postmanPolymorphicBase instanceof Folder).map(postmanPolymorphicBase -> (Folder) postmanPolymorphicBase).toList();
        for (Folder folder : folders) {
            brunoRequestFiles.addAll(folder.getItem().stream().map(this::toBrunoRequestFile).toList());
        }
        return brunoRequestFiles.stream().filter(Objects::nonNull).toList();
    }

    @Named("item")
    default BrunoRequestFile toBrunoRequestFile(PostmanPolymorphicBase fromPoly) {
        if (fromPoly instanceof Item) {
            return toBrunoRequestFile((Item) fromPoly);
        }
        return toBrunoRequestFolderFile(fromPoly);
    }
    default BrunoRequestFile toBrunoRequestFolderFile(PostmanPolymorphicBase fromPoly) {
        if (fromPoly instanceof Folder) {
            ((Folder) fromPoly).getItem().forEach(item -> {
                final BrunoRequestFile brunoRequestFolderFile = toBrunoRequestFolderFile(item);
            });
        }
        return null;
    }

    @Mapping(target = "query", source = "request.url.query")
    @Mapping(target = "meta", source = "item")
    @Mapping(target = "headers", source = "request.header")
    @Mapping(target = "request.auth", source = "request.auth.type")
    @Mapping(target = "request.body", source = "request.body.mode")
    @Mapping(target = "auth", source = "request.auth")
    @Mapping(target = "body.value", source = "request.body.raw")
    @Mapping(target = "body.type", source = "request.body.mode")
    BrunoRequestFile toBrunoRequestFile(Item item);

    @AfterMapping
    default void afterToBrunoRequestFile(@MappingTarget BrunoRequestFile brunoRequestFile, Item item) {
        if (Objects.equals(BodyType.none, brunoRequestFile.getBody().getType()) && StringUtils.isBlank(brunoRequestFile.getBody().getValue())) {
            brunoRequestFile.setBody(null);
        }
        if (brunoRequestFile.getAuth() == null) {
            brunoRequestFile.getRequest().setAuth("none");
        }
        handleEvents(brunoRequestFile, item);
        if (item.getRequest().getBody().getMode() == RAW) {
            BodyType type = mapBodyLanguage(item.getRequest().getBody().getAdditionalProperties());
            type = mapBodyLanguageViaHeader(type, item.getRequest().getHeader());
            brunoRequestFile.getBody().setType(type);
            brunoRequestFile.getRequest().setBody(type);
        }
    }


    default void handleEvents(BrunoRequestFile brunoRequestFile, Item item) {
        final List<Event> events = item.getEvent();
        for (Event event : events) {
            switch (event.getListen()) {
                case "test" : {
                    final Tests tests = new Tests();
                    tests.setValue(StringUtils.join(event.getScript().getExec(), "\n"));
                    brunoRequestFile.setTests(tests);
                    break;
                }
                case "prerequest" : {
                    final PreRequestScript value = new PreRequestScript();
                    value.setValue(StringUtils.join(event.getScript().getExec(), "\n"));
                    brunoRequestFile.setPreRequestScript(value);
                    break;
                }
                case "postresponse" : {
                    final PostResponseScript value = new PostResponseScript();
                    value.setValue(StringUtils.join(event.getScript().getExec(), "\n"));
                    brunoRequestFile.setPostResponseScript(value);
                    break;
                }
            }
        }
    }

    Meta toMeta(Item item);

    default Query mapQuery(List<org.cschoell.postman.model.Query> value) {
        final Query query = new Query();
        value.forEach(query1 -> {
            query.put(query1.getKey(), query1.getValue());
        });
        return query;
    }

    default Headers mapHeaders(List<Header> value) {
        Headers headers = new Headers();
        value.forEach(header -> {
            headers.put(header.getKey(), header.getValue());
        });
        return headers;
    }

    Auth mapAuth(org.cschoell.postman.model.Auth value);

    @AfterMapping
    default Auth afterMapAuth(@MappingTarget Auth auth, org.cschoell.postman.model.Auth value) {
        if (value.getType() == org.cschoell.postman.model.Auth.Type.BASIC) {
            mapAuthAttributes(auth, value.getBasic());
        }
        if (value.getType() == org.cschoell.postman.model.Auth.Type.BEARER) {
            mapAuthAttributes(auth, value.getBearer());
        }
        return auth;
    }

    default void mapAuthAttributes(@MappingTarget Auth auth, List<AuthAttribute> authAttributes) {
        authAttributes.forEach(authAttribute -> mapAuthAttribute(auth, authAttribute));
    }
    default void mapAuthAttribute(@MappingTarget Auth auth, AuthAttribute authAttribute) {
        auth.put(authAttribute.getKey(), authAttribute.getValue());
    }

    @Named("language")
    default BodyType mapBodyLanguage(Map<String,AdditionalProperty> value) {
        if (value == null) return BodyType.text;
        final AdditionalProperty raw = value.get("raw");
        if (raw == null) return BodyType.text;
        String language = raw.getProperties().get("language");
        return StringUtils.isBlank(language) ? BodyType.text : toBodyType(language);
    }

    default BodyType mapBodyLanguageViaHeader(BodyType currentType, List<Header> headers) {
        String contentHeader = headers.stream().filter(header -> StringUtils.equalsIgnoreCase(header.getKey(), "content-type")).map(Header::getValue).findFirst().orElse(null);
        if(StringUtils.contains(contentHeader, "json")) return BodyType.json;
        if(StringUtils.contains(contentHeader, "xml")) return BodyType.xml;
        if(StringUtils.contains(contentHeader, "text")) return BodyType.text;
        return currentType;
    }

    default BodyType toBodyType(Body.Mode mode) {
        return switch (mode) {
            case FILE -> BodyType.none;
            case RAW -> BodyType.text;
            case GRAPHQL -> BodyType.graphql;
            case FORMDATA -> BodyType.multipartForm;
            case URLENCODED -> BodyType.urlEncoded;
        };
    }
    default BodyType toBodyType(String language) {
        return switch (language) {
            case "xml" -> BodyType.xml;
            case "text" -> BodyType.text;
            case "json" -> BodyType.json;
            default -> BodyType.text;
        };
    }


    default String buildUrlString(Url url) {
        if (url.getHost() == null || url.getHost().isEmpty()) return url.getRaw();
        String portString = StringUtils.isNotBlank(url.getPort()) ? ":" + url.getPort() : "";
        return url.getProtocol() + "://" + StringUtils.join(url.getHost(), ".") + portString + "/" + StringUtils.join(url.getPath(), "/");
    }

}
