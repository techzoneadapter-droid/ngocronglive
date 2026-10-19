package real.skill;
public final class SkillSmoke {
    public static void main(String[] args) {
        SkillData.createSkill();
        if (SkillData.nClasss == null || SkillData.nClasss.length < 3)
            throw new AssertionError("Expected all three character classes");
        for (int i = 0; i < 3; i++) {
            NClass type = SkillData.nClasss[i];
            Skill first = type.skillTemplates[0].skills[0];
            if (Skills.get(first.skillId) != first) throw new AssertionError("Starter skill not registered");
            System.out.println("PASS: class=" + type.classId + " starter=" + first.skillId);
        }
    }
}
