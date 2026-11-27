# Compact symbol icons. Each icon is a small grid (about 11x11) of palette
# characters; the renderer centres it on the 16x16 canvas and adds the bevel,
# the dark outline and the drop shadow. 'w' pixels are highlights and are
# never shaded. Rows of 6 chars with m=True are mirrored around the last char.
I = {}


def icon(name, pal, rows, m=False):
    if m:
        rows = [r + r[:-1][::-1] for r in rows]
    I[name] = (pal, rows)


def canvas(w, h):
    return [['.'] * w for _ in range(h)]


def text(c):
    return [''.join(r) for r in c]


W = "ffffff"

icon("serpent_eye", {"g": "3fae3a", "y": "f5d63a", "k": "1a1a10", "w": W}, [
    "...ggggg...",
    ".ggyyyyygg.",
    ".gwyykyyyg.",
    "gwyyykyyyyg",
    "gyyyykyyyyg",
    "gyyyykyyyyg",
    "gyyyykyyyyg",
    "gyyyykyyyyg",
    ".gyyykyyyg.",
    ".ggyyyyygg.",
    "...ggggg...",
])

icon("stone_heart", {"s": "a8a8b4", "c": "4a4a58", "w": "eeeef4"}, [
    ".sss...sss.",
    "swwss.sssss",
    "swssscsssss",
    "sssssscssss",
    "ssssscsssss",
    ".ssscsssss.",
    "..sssssss..",
    "...sssss...",
    "....sss....",
    ".....s.....",
])

icon("wind_feather", {"b": "8fd4ff", "c": "f0fbff"}, [
    "........bb.",
    ".......bbbb",
    "......bbbcb",
    ".....bbbcbb",
    "....bbbcbb.",
    "...bbbcbb..",
    "..bbbcbb...",
    "..bbcbb....",
    "..bcbb.....",
    "..c........",
    ".c.........",
])

icon("mirror_shadow", {"p": "9a6ae0", "c": "7ff8ff"}, [
    "...ppppp...",
    "..ppppppp..",
    ".ppppppppp.",
    ".ppcpppcpp.",
    ".ppcpppcpp.",
    ".ppppppppp.",
    ".ppppppppp.",
    ".ppppppppp.",
    ".ppppppppp.",
    ".pp.ppp.pp.",
    ".p...p...p.",
])

icon("spirit_bell", {"y": "f5c63a", "d": "b07a1a", "c": "8a5a1a", "w": "fff4b0"}, [
    ".....d.....",
    "....yyy....",
    "...ywyyy...",
    "...ywyyy...",
    "..ywyyyyy..",
    "..yyyyyyy..",
    "..yyyyyyy..",
    ".yyyyyyyyy.",
    "yyyyyyyyyyy",
    "....ccc....",
])

icon("time_crystal", {"f": "b8803a", "g": "c8f4f4", "s": "ffd24a"}, [
    ".fffffffff.",
    "..gsssssg..",
    "..gsssssg..",
    "...gsssg...",
    "....gsg....",
    ".....s.....",
    "....gsg....",
    "...ggsgg...",
    "..ggsssgg..",
    "..gsssssg..",
    ".fffffffff.",
])

icon("haunted_compass", {"c": "7a5aa0", "i": "f2e6c8", "r": "e83a3a", "b": "6a6a80", "k": "2a2030"}, [
    "...ccccc...",
    ".cciiiiicc.",
    ".ciiiiiirc.",
    "ciiiiiiriic",
    "ciiiiiriiic",
    "ciiiikiiiic",
    "ciiibiiiiic",
    "ciibiiiiiic",
    ".cbiiiiiic.",
    ".cciiiiicc.",
    "...ccccc...",
])

icon("eternal_ice", {"b": "8ad8ff", "w": W}, [
    ".....b.....",
    "....bbb....",
    ".b...b...b.",
    "..b..b..b..",
    "...bbbbb...",
    "bbbbbwbbbbb",
    "...bbbbb...",
    "..b..b..b..",
    ".b...b...b.",
    "....bbb....",
    ".....b.....",
])

icon("ember_coal", {"r": "e8401c", "o": "ff8a1c", "y": "ffe14a"}, [
    ".....r.....",
    "....rr.....",
    "....rrr....",
    "...rrorr.r.",
    "..rrroorrr.",
    "..rrooorrr.",
    ".rrooyoorr.",
    ".rrooyyoorr",
    ".rroyyyyor.",
    "..rroyyorr.",
    "...rrrrrr..",
])

icon("ancient_root", {"g": "5ccc3a", "s": "3a8a2a", "b": "9a6a3a"}, [
    ".gg...",
    "gggg..",
    "ggggg.",
    ".gggss",
    "...ggs",
    ".....s",
    "..bbbb",
    ".bbbbb",
    "b..b.b",
    "...b..",
], True)

icon("stone_horn", {"c": "eee4cc", "d": "a89070"}, [
    ".........c.",
    "........cc.",
    "........cc.",
    ".......ccc.",
    "......cccc.",
    ".....cccc..",
    "...ccccc...",
    ".ddcccc....",
    "dddccc.....",
    "dddd.......",
    ".dd........",
])

icon("bat_wing", {"b": "7a64a8", "r": "ff4a4a"}, [
    "....b.",
    "b...bb",
    "bb.brb",
    "bbbbbb",
    "bbbbbb",
    ".bb.bb",
    "..b..b",
], True)

icon("chameleon_scale", {"g": "2e9a52", "t": "4fd07a", "y": "bdf27a"}, [
    "....ggg....",
    "...gtttg...",
    "..gyyyyyg..",
    ".gtttttttg.",
    "gyyyyyyyyyg",
    "gtttttttttg",
    ".gyyyyyyyg.",
    "..gtttttg..",
    "...gyyyg...",
    "....gtg....",
    ".....g.....",
])

icon("shackle", {"m": "b4b8c8", "l": "e0e4f0"}, [
    "....mmm....",
    "...m...m...",
    "...m...m...",
    "...m...m...",
    "....mmm....",
    ".....l.....",
    ".....l.....",
    "....mmm....",
    "...m...m...",
    "...m...m...",
    "....mmm....",
])

icon("mirror_glass", {"g": "e0b040", "l": "c8f0ff", "b": "7ac8f0", "h": "9a6a3a", "w": W}, [
    "...ggggg...",
    "..gllllbg..",
    ".glwllllbg.",
    ".gwlllllbg.",
    ".gllllbbbg.",
    "..glbbbbg..",
    "...ggggg...",
    ".....h.....",
    ".....h.....",
    "....hhh....",
])

icon("spider_fang", {"r": "a83a4a", "f": "f4ecd8", "p": "b860ff"}, [
    "..rrrrrrr..",
    ".rrrrrrrrr.",
    ".fffrrrfff.",
    ".fff...fff.",
    "..ff...ff..",
    "..ff...ff..",
    "...ff.ff...",
    "....f.f....",
    "....p.p....",
])

icon("gravity_orb", {"p": "a85ae0", "r": "e8d0ff", "w": W}, [
    "...ppppp...",
    "..pwwpppp..",
    ".pwwpppppp.",
    ".pwppppppp.",
    "rrrrrrrrrrr",
    ".ppppppppp.",
    ".ppppppppp.",
    "..ppppppp..",
    "...ppppp...",
])

icon("miner_core", {"t": "3ad8b4", "T": "9ffbe4", "w": W}, [
    "..ttttttt..",
    ".tTwTtTTTt.",
    "ttttttttttt",
    ".tttttttTt.",
    "..ttttttt..",
    "...ttttt...",
    "....ttt....",
    ".....t.....",
])

icon("wild_flute", {"n": "7ce35a"}, [
    "...nnnnnnn.",
    "...nnnnnnn.",
    "...n.....n.",
    "...n.....n.",
    "...n.....n.",
    "...n.....n.",
    ".nnn...nnn.",
    "nnnn..nnnn.",
    "nnnn..nnnn.",
    ".nn....nn..",
])

icon("thief_mask", {"m": "6a5aa0"}, [
    ".mmmmm",
    "mmmmmm",
    "m..mmm",
    "mm.mmm",
    ".mmmm.",
    "..mm..",
], True)

icon("vampire_tooth", {"r": "e0283c", "w": "ffb0b8"}, [
    ".....r.....",
    ".....r.....",
    "....rrr....",
    "....rrr....",
    "...rrrrr...",
    "..rrwrrrr..",
    "..rwrrrrr..",
    ".rwrrrrrrr.",
    ".rrrrrrrrr.",
    "..rrrrrrr..",
    "...rrrrr...",
])

c = canvas(11, 11)
for y in range(10):
    for x in (9 - y, 10 - y):
        c[y][x] = 'g'
for x, y in ((6, 1), (8, 4), (3, 4), (5, 7), (1, 6), (2, 9)):
    c[y][x] = 'x'
c[0][7] = c[0][8] = c[1][8] = 'L'
icon("thorn_vine", {"g": "3ea83a", "x": "f2e6b0", "L": "8ae85a"}, text(c))

icon("storm_rod", {"b": "4fb8ff", "w": "e8f8ff"}, [
    ".....bbbb..",
    "....bwbb...",
    "...bwbb....",
    "..bwbbbbb..",
    ".....bbb...",
    "....bbb....",
    "...bbb.....",
    "..bb.......",
    ".b.........",
])

icon("tide_pearl", {"p": "dcecf8", "b": "a8c8e8", "w": W}, [
    "..ppppp..",
    ".pwwpppp.",
    "pwwpppppp",
    "pwppppppp",
    "ppppppppp",
    "pppppppbp",
    "ppppppbbp",
    ".ppppbbp.",
    "..ppppp..",
])

icon("golden_scarab", {"g": "f5c23a", "k": "a8741a", "h": "d08a1a", "l": "c07a2a", "a": "c07a2a"}, [
    "...a..",
    "....hh",
    ".l.ggg",
    "l.gggk",
    ".ggggk",
    "lggggk",
    ".ggggk",
    "l.gggk",
    "...ggg",
], True)

c = canvas(11, 11)
for y in range(11):
    for x in range(11):
        if (x - 5) ** 2 + (y - 5) ** 2 <= 5.4 ** 2 and not (x - 7.4) ** 2 + (y - 3.6) ** 2 <= 4.4 ** 2:
            c[y][x] = 'r'
icon("blood_moon", {"r": "e0283c"}, text(c))

icon("sun_shard", {"y": "ffe14a", "o": "ff9a2a", "w": "fff8c0"}, [
    ".....y.....",
    ".y...y...y.",
    "..y.ooo.y..",
    "...ooooo...",
    "..oooyooo..",
    "yyooywyooyy",
    "..oooyooo..",
    "...ooooo...",
    "..y.ooo.y..",
    ".y...y...y.",
    ".....y.....",
])

icon("iron_skin", {"i": "c8ccd8", "d": "8a90a4"}, [
    ".ii...",
    "iiii..",
    "iiiiii",
    ".iiiid",
    ".iiiid",
    ".iiiid",
    ".iiiid",
    ".iiiii",
], True)

c = canvas(11, 11)
for t in range(2, 9):
    for x, y in ((t, t), (t + 1, t), (t, t + 1)):
        if x < 11 and y < 11:
            c[y][x] = 'b'
for cx, cy in ((1.2, 3.0), (3.0, 1.2), (9.0, 7.2), (7.2, 9.0)):
    for y in range(11):
        for x in range(11):
            if (x - cx) ** 2 + (y - cy) ** 2 <= 2.0:
                c[y][x] = 'b'
icon("giant_bone", {"b": "f2ead6"}, text(c))

icon("swift_hoof", {"s": "b8c0d0", "n": "6a7488"}, [
    "..sssss..",
    ".sssssss.",
    "sss...sss",
    "sn.....ns",
    "ss.....ss",
    "sn.....ns",
    "ss.....ss",
    "sss...sss",
])

icon("ender_shard", {"t": "1fa890", "g": "3ae8c4", "k": "3a1458", "w": W}, [
    "...ttttt...",
    ".ttgggggtt.",
    "tgwggkggggt",
    "tgggkkkgggt",
    "tggkkkkkggt",
    "tgggkkkgggt",
    "tggggkggggt",
    ".ttgggggtt.",
    "...ttttt...",
])

icon("magnet_stone", {"s": "e0e4ee", "r": "e8383c"}, [
    "sss.....sss",
    "sss.....sss",
    "rrr.....rrr",
    "rrr.....rrr",
    "rrr.....rrr",
    "rrrr...rrrr",
    ".rrrrrrrrr.",
    "..rrrrrrr..",
])

icon("bear_claw", {"p": "d0844a"}, [
    "..pp...pp..",
    "..pp...pp..",
    "pp.......pp",
    "pp..ppp..pp",
    "...ppppp...",
    "..ppppppp..",
    "..ppppppp..",
    "...ppppp...",
])

icon("mantis_blade", {"g": "7ce84a", "h": "ffcc3a", "b": "9a6a3a", "p": "ffcc3a"}, [
    ".........gg",
    "........ggg",
    ".......ggg.",
    "......ggg..",
    "..h..ggg...",
    "...hggg....",
    "....h......",
    "...b.h.....",
    "..b...h....",
    ".b.........",
    "p..........",
])

icon("turtle_plate", {"g": "2e8a3a", "G": "7ad85a"}, [
    "...ggggg...",
    "..gGGgGGg..",
    ".gGGGgGGGg.",
    ".ggggggggg.",
    "gGGgGGGgGGg",
    "gGGgGGGgGGg",
    ".ggggggggg.",
    ".gGGGgGGGg.",
    "..gGGgGGg..",
    "...ggggg...",
])

c = canvas(11, 11)
for y in range(11):
    for x in range(11):
        d = ((x - 5) ** 2 + (y - 5) ** 2) ** 0.5
        if d <= 1.5:
            c[y][x] = 'r'
        elif d <= 3.0:
            c[y][x] = 'w'
        elif d <= 5.4:
            c[y][x] = 'r'
icon("hunter_mark", {"r": "e8383c", "w": "fff0e8"}, text(c))

icon("executioner_axe", {"a": "d0d4e0", "m": "8a90a4", "h": "a06a3a"}, [
    "a.........a",
    "aa.......aa",
    "aaa.mmm.aaa",
    "aaaammmaaaa",
    "aaa.mmm.aaa",
    "aa...h...aa",
    "a....h....a",
    ".....h.....",
    ".....h.....",
    ".....h.....",
    "....hhh....",
])

icon("shield_crystal", {"s": "3a6ae0", "y": "ffcc3a", "c": "8ff8ff", "w": W}, [
    ".yyyyyyyyy.",
    "ysssssssssy",
    "ysssssssssy",
    "ysssscssssy",
    "yssscccsssy",
    "yssccwccssy",
    ".ysscccssy.",
    "..ysscssy..",
    "...ysssy...",
    "....ysy....",
    ".....y.....",
])

icon("owl_feather", {"b": "a8703a", "y": "ffd84a", "k": "2a1a10", "o": "ff8a2a"}, [
    "b.........b",
    "bb.......bb",
    "bbbbbbbbbbb",
    "byyybbbyyyb",
    "bykybbbykyb",
    "byyybobyyyb",
    ".bbbbobbbb.",
    ".bbbbbbbbb.",
    "..bbbbbbb..",
    "...bbbbb...",
])

icon("mole_claw", {"s": "c8ccd8", "h": "a06a3a"}, [
    ".........hh",
    "........hhh",
    ".......hh..",
    "......hh...",
    ".....hh....",
    "..sssh.....",
    ".sssss.....",
    "ssssss.....",
    "sssss......",
    "ssss.......",
    ".ss........",
])

icon("courage_banner", {"p": "a06a3a", "r": "e8383c", "y": "ffd84a"}, [
    "y..........",
    "prrrrrrrrrr",
    "prrrrrrrrrr",
    "prrrryrrrr.",
    "prrryyyrr..",
    "prrrryrrr..",
    "prrrrrrrrr.",
    "prrrrrrrrrr",
    "p..........",
    "p..........",
    "p..........",
])

icon("essence_siphon", {"c": "a06a3a", "g": "d8ecf4", "p": "c050ff", "w": W}, [
    "....ccc....",
    "....ggg....",
    "...ggggg...",
    "..ggggggg..",
    ".gpwpppppg.",
    ".gwppppppg.",
    ".gpppppppg.",
    "..gpppppg..",
    "...ggggg...",
])

icon("lifebloom", {"p": "ff7ab8", "y": "ffe14a"}, [
    ".ppp...ppp.",
    "ppppp.ppppp",
    "ppppp.ppppp",
    "ppppp.ppppp",
    ".pppyyyppp.",
    "....yyy....",
    ".pppyyyppp.",
    "ppppp.ppppp",
    "ppppp.ppppp",
    "ppppp.ppppp",
    ".ppp...ppp.",
])

icon("obsidian_shard", {"o": "a060e0", "O": "6a2ab0"}, [
    "......o....",
    ".....oo....",
    ".....ooO...",
    "....oooO...",
    "....ooOO...",
    "...oooOO...",
    "...ooOOO...",
    "..oooOOO...",
    "..ooOOO....",
    "...oOO.....",
    "....O......",
])

icon("quake_stone", {"s": "a89a88", "o": "ff8a2a"}, [
    "...sssss...",
    ".sssssssss.",
    "ssssosssss.",
    "sssssosssss",
    "sssssosssss",
    "ssssossssss",
    "sssosssssss",
    ".ssossssss.",
    "..sssssss..",
])

icon("thunder_drum", {"t": "f4e4c4", "y": "ffcc3a", "r": "d83a3a"}, [
    "..ttttttt..",
    ".ttttttttt.",
    ".yyyyyyyyy.",
    ".rryrrryrr.",
    ".rryrrryrr.",
    ".rryrrryrr.",
    ".rryrrryrr.",
    ".yyyyyyyyy.",
    "..rrrrrrr..",
])

icon("purity_charm", {"p": "b8ecff", "w": W}, [
    ".....p.....",
    ".....p.....",
    "....ppp....",
    "....ppp....",
    "...ppwpp...",
    "ppppwwwpppp",
    "...ppwpp...",
    "....ppp....",
    "....ppp....",
    ".....p.....",
    ".....p.....",
])

icon("calm_lotus", {"p": "ff8ac8", "q": "ffd0ea", "g": "4ab86a"}, [
    ".....q.....",
    "....qqq....",
    "p...qqq...p",
    "pp..qqq..pp",
    "ppp.qqq.ppp",
    ".ppppppppp.",
    "..ppppppp..",
    "ggggggggggg",
    ".ggggggggg.",
])

icon("crow_feather", {"f": "6a6aa8", "q": "d0d0f0"}, [
    ".....f.....",
    "....fqf....",
    "...ffqff...",
    "..fffqfff..",
    "..fffqfff..",
    "..fffqfff..",
    "...ffqff...",
    "....fqf....",
    ".....q.....",
    ".....q.....",
])

icon("frost_step", {"b": "5ab8ff", "w": "e8f8ff"}, [
    "wwwwww....",
    ".bbbb.....",
    ".bbbb.....",
    ".bbbb.....",
    ".bbbb.....",
    ".bbbbb....",
    ".bbbbbbb..",
    ".bbbbbbbb.",
    ".wwwwwwww.",
])

icon("soul_flame", {"c": "1fa6d8", "C": "4fe8ff", "w": "e0ffff"}, [
    ".....c.....",
    "....cc.....",
    "....ccc....",
    "...ccCc....",
    "...cCCcc...",
    "..ccCwCcc..",
    "..cCwwwCc..",
    ".ccCwwwCcc.",
    ".cCCwwwCCc.",
    "..cCCCCCc..",
    "...ccccc...",
])

icon("summoning_incense", {"s": "d8b8ff", "o": "ff8a2a", "h": "a06a3a", "b": "e0aa4a"}, [
    "......s....",
    ".....s.....",
    "......s....",
    ".....o.....",
    ".....h.....",
    ".....h.....",
    ".....h.....",
    "..bbbbbbb..",
    ".bbbbbbbbb.",
    "..bbbbbbb..",
    "...bbbbb...",
])

icon("cleansing_water", {"c": "a06a3a", "g": "d8ecf4", "b": "3a9aff", "w": W}, [
    "....ccc....",
    "....ggg....",
    "....ggg....",
    "...ggggg...",
    "...gbbbg...",
    "..gbbbbbg..",
    "..gwbbbbg..",
    ".gwbbbbbbg.",
    ".gbbbbbbbg.",
    ".ggggggggg.",
])

icon("soul_codex", {"p": "7a3ab0", "q": "a858e8", "y": "ffcc3a", "w": "f4ecd8"}, [
    "ppppppppp..",
    "pqqqqqqqpw.",
    "pqqqyqqqpw.",
    "pqqyyyqqpw.",
    "pqyyyyyqpw.",
    "pqqyyyqqpw.",
    "pqqqyqqqpw.",
    "pqqqqqqqpw.",
    "pppppppppw.",
])

# Elemental spirits: one wisp shape, coloured by element.
WISP = [
    ".......oo..",
    "......ooo..",
    "...oooooo..",
    "..oiiiiioo.",
    ".oiiiiiiio.",
    ".oieiiieio.",
    ".oieiiieio.",
    ".oiiiiiiio.",
    "..oiiiiio..",
    "...ooooo...",
    "....o.o....",
]
for _name, _o, _i, _e in (
        ("fire", "ff6a1c", "ffd04a", "7a1a0a"),
        ("light", "ffe680", "fffbe0", "b07a1a"),
        ("water", "2a8aff", "9fd8ff", "0a2a6a"),
        ("death", "c8c0a8", "f4efe0", "c0202a"),
        ("earth", "5aa83a", "b8e07a", "3a2a10"),
        ("darkness", "5a2aa0", "9a6ae0", "ffe14a"),
        ("lightning", "f5c63a", "fff4a0", "3a3a8a"),
        ("stone", "8a8a96", "c8c8d4", "2a2a34")):
    icon("spirit_" + _name, {"o": _o, "i": _i, "e": _e}, WISP)
