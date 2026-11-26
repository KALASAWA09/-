package com.kalasawa.sacredsouls.title;

/** The 9 titles. One holder per server; a player holds at most one. */
public enum SoulTitle {
	WAR_GOD("war_god", "เทพสงคราม", "ฆ่าผู้เล่น 10 คนติดกันโดยไม่ตาย",
			"Resistance I", "ตีโดน 25% ได้เลือดคืน 25% ของดาเมจ"),
	REAPER("reaper", "ยมทูต", "ทำให้ผู้เล่น 3 คนกลายเป็นร่างไร้วิญญาณ",
			"ไม่ติด Wither", "ฆ่าผู้เล่น ได้เลือดคืน 5 หัวใจ"),
	HEIR("heir", "ผู้สืบทอด", "คนแรกในเซิร์ฟที่ตื่นวิญญาณ (เฟส 2)",
			"Health Boost I", "ตีโดน 15% ปิดพลังไอเทม 1 ช่องของศัตรู 5 วิ"),
	DEVOURER("devourer", "ผู้กลืนพันวิญญาณ", "เคยกลืนไอเทมวิญญาณครบ 20 ชนิด",
			"ได้แก่นวิญญาณเพิ่ม 50%", "ตีโดน 20% ขโมยพลังวิญญาณ 1 ดวงไฟ (คนละครั้งต่อ 10 นาที)"),
	FALLEN_KING("fallen_king", "ราชันย์ผู้ล่วงลับ", "เคยถือฉายาอื่นไม่ซ้ำกัน 3 ฉายา",
			"เพื่อนร่วมทีมรอบ 8 บล็อกได้ Strength I", "ตายแล้วระเบิดวิญญาณ ศัตรูรอบตัวติด Weakness II 5 วิ"),
	BERSERKER("berserker", "เบอร์เซิกเกอร์", "ชนะ PvP ตอนเลือดต่ำกว่า 2 หัวใจ 5 ครั้ง",
			"ไม่โดนกระเด็นครึ่งหนึ่ง", "โดนตี 20% สะท้อนดาเมจกลับ 30%"),
	NAMELESS_SHADOW("nameless_shadow", "เงาไร้นาม", "ฆ่าผู้เล่นตอนล่องหน 5 ครั้ง",
			"Night Vision", "ตีโดน 10% ทำให้ศัตรูตาบอด 2 วิ"),
	GUARDIAN("guardian", "ผู้พิทักษ์", "ชนะ Raid 5 ครั้ง",
			"Hero of the Village และเพื่อนร่วมทีมรอบ 8 บล็อกได้ Regeneration I", "ตายครั้งแรกในรอบ 20 นาที รอดเหมือนใช้ Totem"),
	BORDERLESS("borderless", "ผู้ไร้พรมแดน", "ไปครบ 3 มิติ และเดินทางรวม 10,000 บล็อก",
			"Speed I", "โดนตี 15% หลบดาเมจได้ทั้งหมด");

	public final String id;
	public final String thaiName;
	public final String condition;
	public final String buff;
	public final String proc;

	SoulTitle(String id, String thaiName, String condition, String buff, String proc) {
		this.id = id;
		this.thaiName = thaiName;
		this.condition = condition;
		this.buff = buff;
		this.proc = proc;
	}

	public static SoulTitle byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (SoulTitle title : values()) {
			if (title.id.equals(id)) {
				return title;
			}
		}
		return null;
	}
}
