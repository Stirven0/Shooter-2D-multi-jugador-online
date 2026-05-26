package com.aa.shared.message;

public class UseSkillMessage extends Message {
    private int skillSlot;

    public UseSkillMessage() {
        super(MessageType.USE_SKILL);
    }

    public UseSkillMessage(int skillSlot) {
        super(MessageType.USE_SKILL);
        this.skillSlot = skillSlot;
    }

    public int getSkillSlot() { return skillSlot; }
    public void setSkillSlot(int v) { this.skillSlot = v; }
}
