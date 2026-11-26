# Hand-drawn 16x16 icons. Rows of 16 chars, or 8-char halves mirrored (m=True).
I = {}
def icon(name, pal, rows, m=False, over=None):
    I[name] = (pal, rows, m, over or [])

icon("serpent_eye", {"G":"2f6b1f","g":"5fae3a","y":"f2d23c","Y":"c79a1c","k":"111111","w":"ffffff"}, [
"........","........","........",".....GGG","...GGggg","..Gggyyy",".GgyyyyY",".GgyyyYk",
".GgyyyYk",".GgyyyyY","..Gggyyy","...GGggg",".....GGG","........","........","........"], True, [(5,6,"w"),(6,6,"w")])
icon("stone_heart", {"l":"c4c4c4","s":"8a8a8a","d":"5c5c5c","k":"333333","w":"eeeeee"}, [
"........","........","...lsss.","..llssss",".lssssss",".ssssssk",".ssssssk","..ssssss",
"...sssss","....dsss",".....dds","......dd",".......d","........","........","........"], True, [(4,3,"w"),(3,4,"w")])
icon("wind_feather", {"w":"ffffff","b":"bfe9ff","c":"7fb6d6","q":"8a6a4a"}, [
"................","...........ww...","..........wbbw..",".........wbbbw..","........wbbbbw..",".......wbbbcb...",
"......wbbbcbb...",".....wbbbcbbw...","....wbbbcbbw....","....wbbcbbw.....","...wbbcbbw......","...wbcbbw.......",
"...wcbw.........","..qcw...........",".q..............","................"])
icon("mirror_shadow", {"F":"8a63d2","g":"3a2a60","p":"120a20","w":"c8b8ff"}, [
"........",".....FFF","...FFggg","..Fggggg","..Fggggg",".Fggggpp",".Fggggpp",".Fggggpp",
".Fggpppp",".Fgppppp",".Fgppppp","..Fppppp","...FFFFF","........","........","........"], True, [(4,3,"w"),(3,4,"w")])
icon("spirit_bell", {"y":"f2c14e","l":"ffe890","d":"b0811c","c":"6a4a1a","s":"9ff8ff"}, [
"........","......dd",".....dyy","....dyyy","....yyyy","...yyyyy","...yyyyy","...yyyyy",
"..yyyyyy","..yyyyyy",".yyyyyyy",".ddddddd","......cc","......cc","........","........"], True,
 [(4,4,"l"),(4,5,"l"),(4,6,"l"),(3,8,"l"),(3,9,"l"),(1,2,"s"),(14,3,"s"),(13,1,"s")])
icon("time_crystal", {"f":"d4a64a","g":"d8fbfa","s":"5be7e0","d":"8a6a2a"}, [
"........",".fffffff","..gggggg","..gsssss","...gssss","....gsss",".....gss","......gs",
"......gs",".....ggs","....gggs","...gssss","..gsssss",".fffffff","........","........"], True)
icon("haunted_compass", {"c":"4a3a5a","i":"e8dcc0","r":"e03030","b":"505060","k":"1a1a1a","w":"b0ffb0"}, [
"........",".....ccc","...ccccc","..cciiii","..ciiiir",".cciiiir",".ciiiiir",".ciiiiik",
".ciiiiib",".cciiiib","..ciiiib","..cciiii","...ccccc",".....ccc","........","........"], True, [(1,0,"w"),(14,14,"w")])
icon("eternal_ice", {"l":"f0fbff","b":"9fd8ff","d":"5aa5d6","w":"ffffff"}, [
"........","......ll",".....lll","....llbb","...llbbb","..llbbbb",".llbbbbb",".lbbbbbb",
".bbbbbbd","..bbbbdd","...bbddd","....bddd",".....ddd","......dd","........","........"], True, [(6,3,"w"),(5,4,"w")])
icon("ember_coal", {"k":"2b2626","K":"4a4040","o":"ff6a2b","r":"c0301a","y":"ffd04a"}, [
"................","................",".....kkkk.......","...kkKKkkkk.....","..kkKokkkkkkk...","..kKkookkrkkk...",
".kkkkkookrrkkk..",".kkrrkkkoookkk..",".kkkrrkkkkoykk..","..kkkrookkkokk..","..kkkkkykokkk...","...kkkkkkkkk....",
".....kkkkkk.....","................","................","................"])
icon("ancient_root", {"b":"6b4f2a","B":"8a6a3a","g":"5aa83a","G":"8ad35a"}, [
"................","......gggg......",".....gGggGg.....","....ggGgggGg....","......gggg......",".......bb.......",
".......bB.......","......bbbb......",".....bb.bbb.....","....bb...bbb....","...bb..b..bb....","..bb..bb...bb...",
".bb..bb.....bb..",".b..bb.......b..","...b............","................"])
icon("stone_horn", {"c":"d9d2c3","C":"a89e88","d":"7a6f5a"}, [
"................","..c.............","..cc............","..ccc...........","...cCc..........","...ccCc.........",
"....ccCc........","....cccCc.......",".....cccCcc.....","......ccCccc....",".......cccCcd...","........ccccdd..",
"..........cddd..","...........dd...","................","................"])
icon("bat_wing", {"a":"2a1a1a","m":"6a4545","b":"3d2b2b","r":"ff4040"}, [
"........","........","........","......b.","a.....bb","am....bb","amma.rbb","ammmabbb",
"ammmmabb",".mmammbb","..m.m.ab","......b.","........","........","........","........"], True)
icon("chameleon_scale", {"g":"2e8a52","t":"47c27a","y":"a8ec8a","w":"e8ffd8"}, [
"........","........",".....ggg","...ggggg","..gggttt",".ggttttt",".gtttyyy",".gttyyyy",
".gtyyyyy","..tyyyyy","..tyyyyy","...tyyyy","....tyyy",".....tyy","........","........"], True, [(6,7,"w"),(5,8,"w")])
icon("shackle", {"m":"8a8a96","l":"c8c8d0","d":"5a5a66","k":"3a3a44"}, [
"................","...........dd...","..........d..d..","..........d..d..",".....mmmm..dd...","...mmllmmm.d....",
"..mml....mmd....","..ml......mm....",".mm........mm...",".mm........mm...",".mm........mm...","..mm......mm....",
"..mmm....mmm....","...mmmkkmmm.....",".....mkkm.......","................"])
icon("mirror_glass", {"w":"ffffff","l":"cdeffc","b":"8fd0f0"}, [
"................","..........ww....",".........wllw...","........wlllw...",".......wlbllw...","......wllbllw...",
".....wlllblw....","....wllblllw....","...wllbllllw....","...wlbllllw.....","..wlbllllw......","..wbllllw.......",
"..wllllw........","...wwww.........","................","................"])
icon("spider_fang", {"r":"5a1f1f","R":"7a3030","f":"eee6d0","s":"c0b8a0","p":"b050ff"}, [
"........","........",".rrrrrrr",".rRRrrrr","..rrrrrr","..ffffrr","..ffff..","...fsf..",
"...fff..","....fs..","....fp..",".....p..","........","........","........","........"], True)
icon("gravity_orb", {"p":"a35bd6","D":"6a2e9a","r":"e0c0ff","w":"f4e8ff"}, [
"........","........",".....ppp","...ppppp","..pppppp",".ppppppp",".ppppppp","rrrrrrrr",
".ppppppp",".Dpppppp","..DDpppp","...DDDpp",".....DDD","........","........","........"], True, [(4,3,"w"),(5,3,"w"),(4,4,"w")])
icon("miner_core", {"s":"7a7a7a","S":"5a5a5a","t":"4fe3c1","T":"c8fff0"}, [
"................",".ssssssssssssss.",".sssttsssssssSs.",".sstTtsssSsssss.",".ssttsssssttsss.",".sssssSsstTtsss.",
".ssSssssssttsss.",".ssssttsssssssS.",".sssttTtsssSsss.",".sssstsssssssss.",".sSssssssttssss.",".sssssssttTtsSs.",
".ssttssssstssss.",".sstTtsSsssssss.",".ssssssssssssss.","................"])
icon("wild_flute", {"b":"c9a66b","B":"8a6a3a","k":"3a2a1a","n":"ffffff"}, [
"................","............bb..","...nn......bbbb.","...n.n....bbbb..","...n.....bkbb...","..nn....bbbb....",
"..nn...bkbb.....","......bbbb......",".....bkbb.......","....bbbb........","...bbbb.........","..Bbbb..........",
".BBbb...........",".BB.............","................","................"])
icon("thief_mask", {"k":"2b2b2b","g":"555560","r":"7a3ab0"}, [
"........","........","........","........","........",".kkkkkkk","kggggkkk","kkk..kkk",
"kk...kkk",".kkkkkk.","..kkkk..","........","........","........","........","........"], True)
icon("vampire_tooth", {"t":"f4efe6","s":"c9c0b0","r":"b0213a","R":"e04060"}, [
"................","....tttttttt....","...tttttttttt...","...ttttttttts...","....tttttttss...",".....ttttttss...",
".....tttttss....","......tttss.....","......ttss......",".......ts.......",".......rr.......","......rRrr......",
"......rrrr......",".......rr.......","................","................"])
icon("thorn_vine", {"g":"3e8e3a","G":"7acc5a","x":"e8dcb0"}, [
"................","...........GG...","..........gGgx..","........x.ggg...","........ggg.....",".......gggx.....",
"......ggg.......",".....xggg.......","......ggg.......",".....ggg.x......","....ggg.........","...ggg.x........",
"..xggg..........","...gg...........","................","................"])
icon("storm_rod", {"b":"8a98ff","w":"ffffff","d":"4a50c0"}, [
"................",".........bbbbb..","........bbbbb...",".......bbbbb....","......bbbbb.....",".....bbbbbbbbb..",
"........bbbbb...",".......bbbbd....","......bbbbd.....",".....bbbd.......","....bbd.........","...bd...........",
"..b.............","................","................","................"][:16], False, [(10,1,"w"),(9,2,"w"),(8,3,"w"),(7,4,"w"),(6,5,"w")])
icon("tide_pearl", {"p":"e6fbff","w":"ffffff","s":"3fb6c9","S":"2a8a9a"}, [
"........","........","......pp",".....ppp","....pppp","....pppp",".....ppp","s.....pp",
"ss......","sSssssss",".sSsSsSs","..ssssss","....ssss","........","........","........"], True, [(6,3,"w"),(7,3,"w"),(6,4,"w")])
icon("golden_scarab", {"g":"e8b32e","G":"fff0a0","d":"9a6a10","h":"7a5a10","l":"5a4010"}, [
"........",".....l..","......l.",".....hhh","....hhhh","..gggggd","lggggggd",".gGggggd",
".gGggggd",".ggggggd","lggggggd","..gggggd","...ggggd",".....ggd","........","........"], True)
icon("blood_moon", {"r":"c0392b","l":"ff7a6a","d":"7a1a12","w":"ffffff"}, [
"................","......rrrr......","....rlrr........","...rlrr.........","..rlrr..........","..rlr......w....",
".rlrr...........",".rlrr........w..",".rlrr...........",".rlrrr..........","..rrrr......d...","..rrrrrr...dd...",
"...rrrrrrrrd....",".....rrrrdd.....","................","................"])
icon("sun_shard", {"y":"ffd84a","o":"f0a020","w":"fffbe0"}, [
"........",".......y","..y....y","...y....",".....ooo","....oyyy","....oyyy","yy..oyyy",
"yy..oyyy","....oyyy","....oyyy",".....ooo","...y....","..y....y",".......y","........"], True, [(6,5,"w"),(5,6,"w")])
icon("iron_skin", {"i":"b8b8c0","l":"e8e8f0","d":"808088"}, [
"........","........","..iii...",".iiiii..",".iiiiiii",".iiiiiii","...iiiii","...iiiii",
"...iiiii","...iiiii","...iiiii","...iiiii","...ddddd","........","........","........"], True,
 [(2,3,"l"),(2,4,"l"),(4,6,"l"),(4,7,"l"),(4,8,"l"),(4,9,"l")])
icon("swift_hoof", {"s":"b8b8c4","l":"e8e8f0","k":"404048","m":"c08a5a"}, [
"........","........",".....sss","...sssss","..sss...",".sss....",".ss.....",".ks.....",
".ss.....",".ks.....",".ss.....",".sss....","..s.....","........","........","........"], True, [(6,2,"l"),(4,3,"l")])
icon("ender_shard", {"g":"2fb58f","G":"1a6a54","k":"0a2a22","p":"c070ff"}, [
"........",".......g","......gg","......gg",".....ggG",".....gGG","....ggGk","....gGkk",
"....gGkk","....ggGk",".....gGG",".....ggG","......gg","......gg",".......g","........"], True, [(2,3,"p"),(13,10,"p"),(12,2,"p")])
icon("magnet_stone", {"w":"e0e0e8","r":"d04848","d":"902a2a"}, [
"........","........",".www....",".www....",".rrr....",".rrr....",".rrr....",".rrr....",
".rrr....",".rrrr...","..rrrr..","..rrrrrr","....rrrr","........","........","........"], True,
 [(12,4,"d"),(12,5,"d"),(12,6,"d"),(12,7,"d"),(12,8,"d"),(11,9,"d"),(10,10,"d")])
icon("bear_claw", {"w":"f2f2f2","t":"5a3a2a","p":"6a4a3a"}, [
"........","..w..w..","..w..w..",".tt.tt..",".tt.tt..","........","...ppppp","..pppppp",
".ppppppp",".ppppppp",".ppppppp","..pppppp","...ppppp","........","........","........"], True)
icon("mantis_blade", {"g":"8fd14f","G":"d0ff90","b":"4a7a2a"}, [
"................","..gggggg........",".gGGGGGggg......","........gggg....","..........ggg...","...........gg...",
"...........gg...","..........gg....",".........gg.....","........b.......",".......bb.......","......bb........",
".....bb.........","....bb..........","...bb...........","................"])
icon("turtle_plate", {"g":"4e9a56","l":"7cc87a","d":"2e5a32","h":"8ac070"}, [
"........","......hh",".....ddd","...dgggg","..dgggdd",".dgggdll","hdggdlll",".ddddlll",
".dggdlll","hdgggdll","..dgggdd","...dgggg",".....ddd","........","........","........"], True)
icon("hunter_mark", {"o":"e0642d","r":"ff2020"}, [
"........",".......o",".....ooo","...oo..o","..o....o","..o.....",".o......","ooo....r",
"ooo....r",".o......","..o.....","..o....o","...oo..o",".....ooo",".......o","........"], True)
icon("executioner_axe", {"m":"5b5b66","e":"d8d8e0","h":"6a4a2a","r":"a02020"}, [
"................",".......h........","..mmmm.hh.......",".emmmmmhh.......","emmmmmmhh.......","emmmmmmhh.......",
".emmmmmhh.......","..mmmm.hh.......",".......hh.......",".......hh.......",".......rr.......",".......hh.......",
".......hh.......",".......hh.......","................","................"])
icon("shield_crystal", {"b":"3a6aa0","c":"7fc8ff","l":"e0f6ff","w":"ffffff"}, [
"........",".bbbbbbb",".bcccccc",".bcccccc",".bcccccl",".bccccll",".bcccllw",".bccccll",
"..bccccl","..bccccc","...bcccc","....bccc",".....bcc","......bb","........","........"], True)
icon("owl_feather", {"b":"a88b6a","d":"6a5038","c":"e8dcc0","q":"8a6a4a"}, [
"................","...dd...........","..dbbd..........","..dbbbd.........","..ddcbbd........","...dbcbbd.......",
"...dbbcbbd......","....ddbcbbd.....",".....dbbcbd.....","......dbbcbd....",".......ddbcd....","........dbbc....",
".........ddcq...","............qq..",".............q..","................"])
icon("mole_claw", {"p":"e8a0a0","P":"c87878","c":"f8f8e8","d":"6a5040","D":"4a3020"}, [
"................","....pppppppp....","...pppPPPPppp...","...pppppppppp...","....pppppppp....","....c.c..c.c....",
"....c.c..c.c....","....c.c..c.c....","................","..dddddddddddd..","..dDddddDdddDd..","..ddddDddddddd..",
"..dddddddDdddd..","................","................","................"])
icon("courage_banner", {"r":"d43f3f","R":"a02a2a","y":"f2c14e","w":"8a6a3a"}, [
"................","..wwwwwwwwwwww..","...rrrrrrrrrr...","...rrrrrrrrrr...","...rrrryyrrrr...","...rrryyyyrrr...",
"...rryyyyyyrr...","...rrrryyrrrr...","...rrrryyrrrr...","...rrrrrrrrrr...","...rrrr..rrrr...","...rrr....rrr...",
"...rr......rr...","................","................","................"])
icon("essence_siphon", {"c":"8a6a4a","g":"d8c8f0","p":"9b59d0","w":"ffffff"}, [
"........","......cc","......cc","......gg","......gg",".....ggg","...gpppp","..gppppp",
".gpppppp",".gpppppp",".gpppppp","..gppppp","...ggggg","........","........","........"], True, [(4,7,"w"),(3,8,"w"),(3,9,"w")])
icon("lifebloom", {"p":"f07fb0","P":"c04a80","y":"ffe060","s":"3e8e3a","l":"5aa83a"}, [
"........","......pp",".....ppp","..pp.ppp",".ppppPyy",".ppppPyy","..pp.ppp",".....ppp",
"......pp",".......s","...ll..s","....llls",".......s",".......s","........","........"], True)
icon("obsidian_shard", {"o":"3a2552","O":"7a5aa0","p":"b070ff"}, [
"................",".........oo.....","........oOoo....",".......oOooo....","......oOooooo...",".....oOoooooo...",
"....oOooopoooo..","....ooooppoooo..","...oooopppooo...","...ooooppoooo...","..ooooooooooo...","..oooooooooo....",
"...oooooooo.....",".....oooo.......","................","................"])
icon("quake_stone", {"r":"9c7a4e","k":"3a2a1a","y":"ffcc55","g":"5a4a3a"}, [
"........","..y...y.","...y..y.",".....rrr","...rrrrr","..rrrrrk",".rrrrrrk",".rrrrrkr",
".rrrrrkr",".rrrrrrk",".rrrrrrk","..rrrrrr","...rrrrr","........","gggggggg","........"], True)
icon("thunder_drum", {"w":"f0e0c0","b":"6a4a2a","r":"b03a2a","y":"f2c14e","s":"c8a070"}, [
"........","........","...wwwww",".wwwwwww",".bbbbbbb",".ryrrryr",".rryryrr",".rrryrrr",
".rryryrr",".ryrrryr",".bbbbbbb","...bbbbb","........","........","........","........"], True,
 [(2,0,"s"),(3,1,"s"),(13,0,"s"),(12,1,"s")])
icon("purity_charm", {"w":"f5f5ff","s":"c8c8e0","g":"f2c14e","c":"8a6a4a"}, [
"........",".c......","..c.....","...c....","....c...",".....c..","......ww","....wwww",
"...wwwwg","...wwggg","...wwwwg","...swwwg","....swww","......ww","........","........"], True)
icon("calm_lotus", {"p":"f4b8e4","P":"e070c0","g":"3e8e3a","G":"5aae5a"}, [
"........","........","........",".......p","......pP","..p..ppP","..pp.pPP",".ppppPPP",
".pppppPP","..pppppp",".GGGGGGG","gggggggg",".ggggggg","........","........","........"], True)
icon("crow_feather", {"k":"2a2a3a","b":"4a5a9a","q":"6a6a78"}, [
"................",".......kk.......","......kkkk......","......kbkk......",".....kkbkkk.....",".....kkbkkk.....",
".....kbkkkk.....",".....kkbkkk.....",".....kkbkkk.....","......kbkk......","......kbkk......",".......bk.......",
".......q........",".......q........","................","................"])
icon("frost_step", {"b":"bfefff","w":"ffffff","i":"6ac0f0","s":"e0f8ff"}, [
"................","....wwww........","....bbbb........","....bbbb........","....bbbb........","....bbbb........",
"....bbbbb.......","....bbbbbbbb....","...bbbbbbbbbb...","...bbbbbbbbbb...","...iiiiiiiiii...","................",
"..s.....s....s..","................","................","................"], False, [(12,3,"s"),(13,5,"s"),(1,6,"s")])
# extras
icon("soul_flame", {"f":"3fd0f0","F":"9ff8ff","w":"ffffff"}, [
"........",".......f","......ff","......ff",".....fff","....ffFF","...ffFFF","..fffFFw",
"..ffFFww","..ffFFww","..ffFFFw","...ffFFF","....ffff","........","........","........"], True)
icon("summoning_incense", {"s":"c8c0e0","e":"ff6a2b","t":"8a5a3a","h":"c98a4b","H":"8a5a2a"}, [
"................",".......s........","......s.........",".......s........","........s.......",".......e........",
".......t........",".......t........",".......t........",".......t........",".......t........","....hhhhhhh.....",
"...hhhhhhhhh....","....HHHHHHH.....","................","................"])
icon("cleansing_water", {"c":"8a6a4a","g":"dff4ff","b":"5ab8ff","w":"ffffff"}, [
"........","......cc","......gg","......gg","......gg",".....gbb","....gbbb","...gbbbb",
"..gbbbbb",".gbbbbbb",".gbbbbbb",".ggggggg","........","........","........","........"], True, [(5,6,"w"),(4,7,"w")])
icon("soul_codex", {"s":"4a2a80","p":"7a4cc9","g":"f2c14e","c":"6ff2ff","w":"ffffff","W":"f0e8d8"}, [
"................","..sppppppppppp..","..sggggggggggp..","..sgppppppppgp..","..sgpppccpppgp..","..sgppcwwcppgp..",
"..sgppcwwcppgp..","..sgpppccpppgp..","..sgppppppppgp..","..sggggggggggp..","..sppppppppppp..","..sWWWWWWWWWWW..",
"..sssssssssss...","................","................","................"])
# --- second pass redraws ---
icon("stone_horn", {"c":"d9d2c3","C":"f4efe4","d":"8a7f6a","r":"b0a690"}, [
"................","..........cc....","........ccCCc...","......ccCCCCc...",".....cCCcrcc....","....cCcr........",
"...cCcc.........","...cCrc.........","..cCcc..........","..cCc...........","..cCr...........","..dcc...........",
"..ddc...........","...dd...........","................","................"])
icon("mirror_glass", {"f":"9a9ab8","l":"bfe6f8","w":"ffffff","b":"88c8e8"}, [
"................",".ffffffffffffff.",".fllwlllllllllf.",".flwllllllllwlf.",".fwllllllllwllf.",".fllllllllwlllf.",
".flllllllwllllf.",".fllllllwlllllf.",".flllllwllllllf.",".fllllwlllllllf.",".flllwlllllllwf.",".fllwlllllllwlf.",
".flllllllllwllf.",".fbbbbbbbbbbbbf.",".ffffffffffffff.","................"])
icon("bear_claw", {"w":"f2f2f2","r":"c03030"}, [
"................",".w....w....w....",".ww...ww...ww...",".ww...ww...ww...","..ww...ww...ww..","..ww...ww...ww..",
"..wr...wr...wr..","...ww...ww...ww.","...ww...ww...ww.","...wr...wr...wr.","....ww...ww...ww","....wr...wr...wr",
".....r....r....r","................","................","................"])
icon("quake_stone", {"r":"9c7a4e","R":"c8a070","k":"3a2a1a","y":"ffcc55","g":"5a4a3a","G":"7a6a5a"}, [
"................","..y.....y....y..","...y....y...y...",".......rrrr.....",".....rrRRrrrr...","....rRRrrkrrrr..",
"...rRrrrkkrrrrr.","...rrrrrkrrrrrr.","..rrrrrrrkrrrrr.","..rrrrrrkkrrrr..","..rrrrrkrrrrrr..","...rrrrkrrrrr...",
"gggggggggggggggg","gGgggGgggGggggGg","................","................"])
icon("executioner_axe", {"m":"5b5b66","e":"d8d8e0","h":"6a4a2a","r":"a02020"}, [
"................","........hh......","..eemmm.hh......",".emmmmmmhh......","emmmmmmmhh......","emmmmmmmhh......",
"emmmmmmmhh......",".emmmmmmhh......","..eemmm.hh......","........hh......","........rr......","........hh......",
"........hh......","........hh......","........hh......","................"])
icon("mole_claw", {"s":"a0a0aa","S":"d0d0d8","h":"6a4a2a","g":"3a2a1a","d":"6a5040"}, [
"................","..........sdd...",".........ssddd..","........sSsssd..","........sSssss..",".......hsssss...",
"......hh........",".....hh.........","....hh..........","...hh...........","..gg............",".ggg............",
"..g.............","................","................","................"])
