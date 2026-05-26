package com.aa.shared.message;

import java.util.List;

public class RoomUpdatedMessage extends Message {
    private String roomId;
    private List<String> playerIds;
    private String status;
    private String hostId;

    public RoomUpdatedMessage() {
        super(MessageType.ROOM_UPDATED);
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    
    public List<String> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<String> playerIds) { this.playerIds = playerIds; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
}