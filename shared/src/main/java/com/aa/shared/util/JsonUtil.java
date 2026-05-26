package com.aa.shared.util;

import com.aa.shared.message.*;
import com.aa.shared.model.*;
import com.google.gson.*;
import java.lang.reflect.Type;

/**
 * Utilidad para serialización/deserialización JSON.
 * Configurada para manejar la jerarquía de mensajes correctamente.
 */
public class JsonUtil {

    // Gson simple: tiene Vector2Adapter pero NO MessageAdapter.
    // Se usa para deserializar subclases concretas sin recursión.
    private static final Gson gsonPlain;

    // Gson completo: con MessageAdapter para polimorfismo.
    // Se usa para la API pública (serializar/deserializar genérico).
    private static final Gson gson;

    static {
        // 1. Gson base con adapters de campo, SIN MessageAdapter
        GsonBuilder plainBuilder = new GsonBuilder();
        plainBuilder.serializeNulls();
        plainBuilder.registerTypeAdapter(Vector2.class, new Vector2Adapter());
        gsonPlain = plainBuilder.create();

        // 2. Gson público: reutiliza gsonPlain internamente
        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        builder.serializeNulls();
        builder.registerTypeAdapter(Message.class, new MessageAdapter());
        builder.registerTypeAdapter(Vector2.class, new Vector2Adapter());
        gson = builder.create();
    }

    // ==================== SERIALIZACIÓN ====================

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static String toJson(Object obj, Type type) {
        return gson.toJson(obj, type);
    }

    // ==================== DESERIALIZACIÓN ====================

    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    public static <T> T fromJson(String json, Type type) {
        return gson.fromJson(json, type);
    }

    /**
     * Deserializa un mensaje genérico (útil cuando no sabes el tipo).
     * Retorna la subclase correcta según el campo "type".
     */
    public static Message parseMessage(String json) {
        return gson.fromJson(json, Message.class);
    }

    /**
     * Deserializa con tipo específico para mejor performance.
     */
    public static MoveMessage parseMoveMessage(String json) {
        return gsonPlain.fromJson(json, MoveMessage.class);
    }

    public static GameStateMessage parseGameStateMessage(String json) {
        return gsonPlain.fromJson(json, GameStateMessage.class);
    }

    // ==================== ADAPTADORES PERSONALIZADOS ====================

    /**
     * Adaptador para deserializar Message según su tipo.
     */
    private static class MessageAdapter
        implements JsonSerializer<Message>, JsonDeserializer<Message>
    {

        @Override
        public JsonElement serialize(
            Message src,
            Type typeOfSrc,
            JsonSerializationContext context
        ) {
            JsonObject result = gsonPlain
                .toJsonTree(src, src.getClass())
                .getAsJsonObject();
            // Asegurar que el tipo esté presente
            if (!result.has("type")) {
                result.addProperty("type", src.getType().name());
            }
            return result;
        }

        @Override
        public Message deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext context
        ) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            if (!jsonObject.has("type")) {
                throw new JsonParseException("Message sin campo 'type'");
            }

            String typeStr = jsonObject.get("type").getAsString();
            MessageType type;
            try {
                type = MessageType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException(
                    "Tipo de mensaje desconocido: " + typeStr
                );
            }

            // Deserializar según el tipo específico
            Class<? extends Message> targetClass = getTargetClass(type);
            return gsonPlain.fromJson(json, targetClass);
        }

        private Class<? extends Message> getTargetClass(MessageType type) {
            return switch (type) {
                case LOGIN_REQUEST -> LoginMessage.class;
                case LOGIN_RESPONSE -> LoginResponseMessage.class;
                case CREATE_ROOM -> LoginMessage.class; // o crear CreateRoomMessage si quieres
                case ROOM_CREATED -> RoomCreatedMessage.class;
                case ROOM_UPDATED -> RoomUpdatedMessage.class;
                case JOIN_ROOM_RESPONSE -> JoinRoomResponseMessage.class;
                case GAME_START -> Message.class; // o crear GameStartMessage
                case MOVE_INPUT -> MoveMessage.class;
                case SHOOT_INPUT -> ShootMessage.class;
                case SWAP_WEAPON -> SwapWeaponMessage.class;
                case USE_SKILL -> UseSkillMessage.class;
                case BUFF_UPDATE -> BuffUpdateMessage.class;
                case GAME_STATE -> GameStateMessage.class;
                case GAME_END -> GameEndMessage.class;
                case ERROR -> ErrorMessage.class;
                case PING -> PingMessage.class;
                case PONG -> PongMessage.class;
                case ROOM_LIST_RESPONSE -> RoomListResponseMessage.class;
                case RECONNECT -> ReconnectMessage.class;
                case IDLE_WARNING -> IdleWarningMessage.class;
                case KICKED_IDLE -> KickedIdleMessage.class;
                default -> throw new JsonParseException(
                    "Tipo de mensaje no mapeado a clase concreta: " + type
                );
            };
        }
    }

    /**
     * Adaptador para Vector2 (más compacto que objeto con fields).
     */
    private static class Vector2Adapter
        implements JsonSerializer<Vector2>, JsonDeserializer<Vector2>
    {

        @Override
        public JsonElement serialize(
            Vector2 src,
            Type typeOfSrc,
            JsonSerializationContext context
        ) {
            JsonArray array = new JsonArray();
            array.add(src.x());
            array.add(src.y());
            return array;
        }

        @Override
        public Vector2 deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext context
        ) throws JsonParseException {
            JsonArray array = json.getAsJsonArray();
            double x = array.get(0).getAsDouble();
            double y = array.get(1).getAsDouble();
            return new Vector2(x, y);
        }
    }

    // ==================== UTILIDADES ADICIONALES ====================

    /**
     * Crea un JsonObject para inspección manual.
     */
    public static JsonObject parseToObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * Extrae un campo específico sin parsear todo el objeto.
     */
    public static String extractField(String json, String fieldName) {
        JsonObject obj = parseToObject(json);
        return obj.has(fieldName) ? obj.get(fieldName).getAsString() : null;
    }
}
