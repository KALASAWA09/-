package com.kalasawa.sacredsouls.power;

/**
 * The 20 soul items. Each one gives one passive power when devoured.
 * Numbers live in {@link PowerHandler}; this enum only holds identity and display data.
 */
public enum SoulPower {
	SERPENT_EYE("serpent_eye", "ดวงตาอสรพิษ", 0x7FD14B, "เห็นผู้เล่นที่ล่องหนหรือย่อตัวในรัศมี 16/20/24 บล็อกเป็นเงาเรืองแสง"),
	STONE_HEART("stone_heart", "หัวใจศิลา", 0x8A8A8A, "ดาเมจครั้งเดียวไม่เกิน 6 หัวใจ คูลดาวน์ 15/12/9 วิ"),
	WIND_FEATHER("wind_feather", "ขนนกลม", 0xBFE9FF, "กระโดดซ้ำกลางอากาศได้ 1 ครั้ง (ขั้น 3: 2 ครั้ง)"),
	MIRROR_SHADOW("mirror_shadow", "เงาสะท้อน", 0x4B2E83, "เลือดต่ำกว่า 4 หัวใจ หายตัวพร้อมควันลวงตา 2/3/4 วิ (ทุก 60/50/40 วิ)"),
	SPIRIT_BELL("spirit_bell", "ระฆังวิญญาณ", 0xF2C14E, "ผู้เล่นนอกทีมเข้าใกล้ 20/26/32 บล็อก ได้ยินระฆังและเขาเรืองแสง 3 วิ"),
	TIME_CRYSTAL("time_crystal", "ผลึกย้อนกาล", 0x5BE7E0, "เลือดต่ำกว่า 2 หัวใจ ย้อนไปตำแหน่งและเลือดเมื่อ 4 วิก่อน (ทุก 5/4/3 นาที)"),
	HAUNTED_COMPASS("haunted_compass", "เข็มทิศหลอน", 0xB34A4A, "บอกทิศและระยะผู้เล่นนอกทีมที่ใกล้ที่สุด (200/400/800 บล็อก)"),
	ETERNAL_ICE("eternal_ice", "น้ำแข็งนิรันดร์", 0x9FD8FF, "ตีโดนมีโอกาส 15/20/25% แช่แข็งศัตรู 1/1.5/2 วิ"),
	EMBER_COAL("ember_coal", "ถ่านอัคคี", 0xFF6A2B, "ไม่ติดไฟ ตีศัตรูที่ติดไฟแรงขึ้น 30/40/50%"),
	ANCIENT_ROOT("ancient_root", "รากไม้โบราณ", 0x6B4F2A, "ยืนนิ่ง 3 วิ ฟื้นเลือดเร็วขึ้นและไม่โดนกระเด็น"),
	STONE_HORN("stone_horn", "เขาแพะหิน", 0xD9D2C3, "วิ่งเร็วชนศัตรู ศัตรูกระเด็นไกลและมึน 1 วิ"),
	BAT_WING("bat_wing", "ปีกค้างคาว", 0x3D2B2B, "อยู่ในที่มืดได้ Speed II และมองเห็นในความมืด"),
	CHAMELEON_SCALE("chameleon_scale", "เกล็ดกิ้งก่า", 0x47C27A, "ย่อนิ่ง 2/1.5/1 วิแล้วล่องหน จนกว่าจะขยับหรือตี"),
	SHACKLE("shackle", "โซ่ตรวน", 0x6E6E78, "ตีโดนมีโอกาส 15/20/25% ผูกโซ่ศัตรูไว้ ห่างจากเราไม่เกิน 6 บล็อก 4 วิ"),
	MIRROR_GLASS("mirror_glass", "กระจกสะท้อน", 0xE6F7FF, "ลูกธนูและตรีศูลที่ยิงใส่มีโอกาส 30/40/50% สะท้อนกลับไปหาคนยิง"),
	SPIDER_FANG("spider_fang", "เขี้ยวแมงมุม", 0x5A1F1F, "ปีนกำแพงได้ ตีโดน 20% วางใยใต้เท้าศัตรู และ 10% ใส่พิษ I 2 วิ"),
	GRAVITY_ORB("gravity_orb", "ลูกแก้วแรงโน้มถ่วง", 0xA35BD6, "ตกจากที่สูงลงพื้น ศัตรูรอบ 4/5/6 บล็อกถูกดูดเข้ามาและลอยขึ้น 1.5 วิ"),
	MINER_CORE("miner_core", "แก่นนักขุด", 0x4FE3C1, "บอกแร่หายากที่ใกล้ที่สุดในรัศมี 6/8/10 บล็อก"),
	WILD_FLUTE("wild_flute", "ขลุ่ยป่า", 0xC9A66B, "เลือดต่ำกว่าครึ่ง หมาป่าวิญญาณ 2/2/3 ตัวโผล่มาช่วยสู้ 15 วิ (ทุก 90/75/60 วิ)"),
	THIEF_MASK("thief_mask", "หน้ากากโจร", 0x2B2B2B, "ตีโดนมีโอกาส 10/15/20% ขโมยเอฟเฟกต์ยาดี 1 อย่างของศัตรูมาเป็นของเรา");

	public final String id;
	public final String thaiName;
	public final int color;
	public final String description;

	SoulPower(String id, String thaiName, int color, String description) {
		this.id = id;
		this.thaiName = thaiName;
		this.color = color;
		this.description = description;
	}

	public static SoulPower byId(String id) {
		for (SoulPower power : values()) {
			if (power.id.equals(id)) {
				return power;
			}
		}
		return null;
	}
}
