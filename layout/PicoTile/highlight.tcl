gui::clear_highlights -1
select -type Inst -name "pico*"   -highlight 7
select -type Inst -name "pico.core*"   -highlight 2
select -type Inst -name "reqNi*" -highlight 5
select -type Inst -name "respNi*" -highlight 6
select -type Inst -name "reqRouter*" -highlight 0
select -type Inst -name "respRouter*" -highlight 3

gui::clear_highlights -1
select -type Inst -name "mesh.s4nocReq*"   -highlight 3
select -type Inst -name "mesh.s4nocResp*"   -highlight 4
select -type Inst -name "mesh.cores_0*"   -highlight 2
select -type Inst -name "mesh.cores_1*"   -highlight 0
select -type Inst -name "mesh.cores_2*"   -highlight 1
select -type Inst -name "mesh.cores_3*"   -highlight 5
select -type Inst -name "mesh.cores_4*"   -highlight 7
select -type Inst -name "mesh.cores_5*"   -highlight 8


gui::clear_highlights -1
select -type Inst -name "array.coreTiles_0*"   -highlight 1
select -type Inst -name "array.coreTiles_1*"   -highlight 2
select -type Inst -name "array.coreTiles_2*"   -highlight 3
select -type Inst -name "array.coreTiles_3*"   -highlight 4
select -type Inst -name "array.coreTiles_4*"   -highlight 5
select -type Inst -name "array.coreTiles_5*"   -highlight 6
select -type Inst -name "array.accessTile*"   -highlight 7

select -type Inst -name "array.coreTiles_0.reqNi*"   -highlight 8
select -type Inst -name "array.coreTiles_1.reqNi*"   -highlight 8
select -type Inst -name "array.coreTiles_2.reqNi*"   -highlight 8
select -type Inst -name "array.coreTiles_3.reqNi*"   -highlight 8
select -type Inst -name "array.coreTiles_4.reqNi*"   -highlight 8
select -type Inst -name "array.coreTiles_5.reqNi*"   -highlight 8


select -type Inst -name "array.coreTiles_0.respNi*"   -highlight 8
select -type Inst -name "array.coreTiles_1.respNi*"   -highlight 8
select -type Inst -name "array.coreTiles_2.respNi*"   -highlight 8
select -type Inst -name "array.coreTiles_3.respNi*"   -highlight 8
select -type Inst -name "array.coreTiles_4.respNi*"   -highlight 8
select -type Inst -name "array.coreTiles_5.respNi*"   -highlight 8

select -type Inst -name "array.coreTiles_0.respRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_1.respRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_2.respRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_3.respRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_4.respRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_5.respRouter*"   -highlight 8

select -type Inst -name "array.coreTiles_0.reqRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_1.reqRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_2.reqRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_3.reqRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_4.reqRouter*"   -highlight 8
select -type Inst -name "array.coreTiles_5.reqRouter*"   -highlight 8