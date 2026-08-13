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
