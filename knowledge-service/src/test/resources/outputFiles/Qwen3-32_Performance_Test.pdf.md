Qwen3-32 模型服务性能测试结果

（Agent场景）

Model Service Benchmark Report

Test Config Script version: 5 ﻿ Generated at: 2026-03-22T11:01:51 ﻿ Base URL: http://127.0.0.1:8000/v1\ ﻿ Mode: bench ﻿ Models: Qwen3-32B ﻿ Context sizes: 128,512,2048,4096,8192 ﻿ Bench testset: model_service_testset.json ﻿ Thinking mode: no_think ﻿ Max output tokens: 128 ﻿ Temperature: 0.0 ﻿ Timeout per request: 1800s ﻿ First token timeout: 60s ﻿

Tokenizer Status Qwen3-32B -> rough_estimate (No module named 'transformers') ﻿

Environment Baseline Hostname: hw-xn-cd-rbt-4-dvznt-001 ﻿ Platform: linux ﻿ OS: Ubuntu 20.04.6 LTS ﻿ Kernel: Linux hw-xn-cd-rbt-4-dvznt-001 5.4.0-162-generic #179-Ubuntu SMP Mon Aug 14 0

8:51:31 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux ﻿ Python: 3.9.5 ﻿

●

●

●

●

●

●

●

●

●

●

●

●

●

●

●

●

●

●

1 / 56

CPU model: Intel Xeon Processor (Cascadelake) ﻿ CPU logical cores: 32 ﻿ CPU physical cores: 16 ﻿ Total memory (GB): 251.889 ﻿ Root disk total/free (GB): 245.956 / 73.609 ﻿ GPU summary: GPU 0: Tesla V100S-PCIE-32GB (UUID: GPU-3beb1d35-b4eb-59a5-89d8-c31494

4a6847); GPU 1: Tesla V100S-PCIE-32GB (UUID: GPU-dfefd7fe-ee7c-0a25-2da2-03666ec762b

b); GPU 2: Tesla V100S-PCIE-32GB (UUID: GPU-d7586c8e-d0ce-6736-491e-625344696468); GPU

3: Tesla V100S-PCIE-32GB (UUID: GPU-086b7df6-bf85-67f1-13f0-6e92592ad511) ﻿ nvidia-smi summary: Tesla V100S-PCIE-32GB, 32768, 535.230.02; Tesla V100S-PCIE-32G

B, 32768, 535.230.02; Tesla V100S-PCIE-32GB, 32768, 535.230.02; Tesla V100S-PCIE-32GB,

32768, 535.230.02 ﻿

Model Summary

Bench Results

●

●

●

●

●

●

●

Model Smoke Success

Bench Success

Avg TTFT (s)

Max TTFT (s)

Avg Output

TPS

Avg GPU

Util (%)

Avg KV Cache

(%)

Qwen3- 32B

- 16/16 11.849 15.649 17.292 92.859 0.000

Mod el

Case Pro mpt Toke ns (thin k/no _thin k)

Thin k OK

NoT hink OK

Thin k

TTFT (s)

NoT hink TTFT

(s)

Thin k

Total (s)

NoT hink Total

(s)

Thin k

Com pleti

on

NoT hink Com pleti

on

NoT hink Outp ut

2 / 56

Bench Per-Mode Details

Qwe n3- 32B

step _3

7790 /779 1

Y Y 9.92 4

9.91 9

18.9 76

10.2 77

208 4 OK 。

Qwe n3- 32B

step _5

1150 7/11 509

Y Y 10.0 64

9.91 3

19.1 64

10.1 98

200 4 OK

Qwe n3- 32B

step _8

1673 9/16 740

Y Y 15.5 78

15.6 49

24.6 42

15.9 33

195 4 OK

Qwe n3- 32B

step _10

1938 5/19 386

Y Y 11.1 32

11.1 58

20.1 97

11.4 40

181 4 OK

Qwe n3- 32B

step _12

2165 0/21 651

Y Y 10.0 13

10.0 17

19.0 67

10.2 97

190 4 OK

Qwe n3- 32B

step _15

2503 7/25 038

Y Y 15.3 95

15.4 35

24.4 16

15.7 14

182 4 OK

Qwe n3- 32B

step _18

2789 2/27 894

Y Y 14.4 92

14.4 97

23.5 17

14.7 82

177 4 OK

Qwe n3- 32B

step _20

2942 5/29 427

Y Y 8.40 3

7.99 2

17.4 43

8.28 5

177 4 OK

3 / 56

Mo del

Cas e

Mo de

Pro mpt

Tok ens

Suc ces

s

HT TP

TTF T (s)

Tot al (s)

Dec ode (s)

Co mpl etio

n Tok ens

Out put TP

S

Ch unk

s

Out put Pre vie w

Ser vice Met rics

Qw en3 -32 B

ste p_3

thin k

779 0

Y 200 9.9 24

18. 976

9.0 53

208 22. 976

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

关

于

检

索

链

路

在

大

促

压

测

期

间

出

现

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =0. 00 % gpu _uti l_av g=8 6.0 0% gpu _m em _uti

4 / 56

的

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

用

户

已

经

提

供

了

详

细

的

运

行

手

册

检

索

结

果

、

最

近

l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

5 / 56

的

变

更

记

录

以

及

监

控

摘

要

。

我

的

任

务

是

根

据

这

些

信

息

继

续

调

查

问

题

，

并

按

照

Re Act 运

维.. .

6 / 56

Qw en3 -32 B

ste p_3

no_ thin k

779 1

Y 200 9.9 19

10. 277

0.3 58

4 11. 168

8 OK 。

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =0. 62 % gpu _uti l_av g=8 8.0 0% gpu _m em _uti l_av g=8 6.8 9% gpu _m em

7 / 56

_us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_5

thin k

115 07

Y 200 10. 064

19. 164

9.1 00

200 21. 979

129 好

的

，

我

现

在

需

要

处

理

用

户

关

于

检

索

链

路

在

大

促

压

测

期

间

出

现

延

迟

抖

动

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =64 .51 % gpu _uti l_av g=8 4.5 0% gpu _m em _uti

8 / 56

和

局

部

召

回

空

结

果

的

问

题

。

根

据

之

前

的

Re Act 运

维

代

理

的

执

行

历

史

，

已

经

完

成

了

运

行

手

册

l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

9 / 56

检

索

、

最

近

变

更

检

查

、

监

控

摘

要

分

析

、

关

键

错

误

日

志

查

看

以

及

配

置

差

异

对

比

。

现

在

需.. .

10 / 56

Qw en3 -32 B

ste p_5

no_ thin k

115 09

Y 200 9.9 13

10. 198

0.2 85

4 14. 042

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =64 .49 % gpu _uti l_av g=9 5.2 5% gpu _m em _uti l_av g=8 6.8 9% gpu _m em

11 / 56

_us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_8

thin k

167 39

Y 200 15. 578

24. 642

9.0 64

195 21. 514

129 好

的

，

我

现

在

需

要

处

理

用

户

关

于

检

索

链

路

在

大

促

压

测

期

间

出

现

延

迟

抖

动

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =70 .78 % gpu _uti l_av g=9 1.5 0% gpu _m em _uti

12 / 56

和

局

部

召

回

空

结

果

的

问

题

。

根

据

之

前

的

对

话

历

史

，

用

户

已

经

进

行

了

多

个

步

骤

的

排

查

，

包

l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

13 / 56

括

运

行

手

册

检

索

、

最

近

变

更

检

查

、

监

控

摘

要

分

析

、

错

误

日

志

查

看

、

配

置

差

异

对

比

、

工

单

14 / 56

反

馈.. .

Qw en3 -32 B

ste p_8

no_ thin k

167 40

Y 200 15. 649

15. 933

0.2 84

4 14. 106

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =70 .76 % gpu _uti l_av g=9 4.5 0% gpu _m em _uti l_av g=8 6.8 9%

15 / 56

gpu _m em _us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_1 0

thin k

193 85

Y 200 11. 132

20. 197

9.0 65

181 19. 968

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

即

检

索

链

路

在

大

促

压

测

期

间

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =85 .45 % gpu _uti l_av g=9 2.7 5%

16 / 56

出

现

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

用

户

希

望

我

像

一

个

Re Act 运

维

代

理

一

样

逐

步

调

查

gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

17 / 56

，

优

先

检

查

运

行

手

册

、

最

近

变

更

、

监

控

、

日

志

、

配

置

、

工

单

和

集

群

事

件

，

再

决

定

下

一

步

18 / 56

。 ...

Qw en3 -32 B

ste p_1 0

no_ thin k

193 86

Y 200 11. 158

11. 440

0.2 83

4 14. 151

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =85 .44 % gpu _uti l_av g=9 4.2 5% gpu _m em _uti l_av g=8 6.8 9%

19 / 56

gpu _m em _us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_1 2

thin k

216 50

Y 200 10. 013

19. 067

9.0 54

190 20. 985

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

即

检

索

链

路

在

大

促

压

测

期

间

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =89 .84 % gpu _uti l_av g=9 3.5 0%

20 / 56

出

现

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

根

据

之

前

的

工

具

调

用

和

结

果

，

我

需

要

综

合

所

有

gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

21 / 56

信

息

来

确

定

根

本

原

因

并

提

出

解

决

方

案

。 首

先

，

回

顾

之

前

的

步

骤

，

用

户

已

经

通

过

多

个

工

具

22 / 56

调

用

来

收.. .

Qw en3 -32 B

ste p_1 2

no_ thin k

216 51

Y 200 10. 017

10. 297

0.2 80

4 14. 306

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =89 .82 % gpu _uti l_av g=1 00. 00 % gpu _m em _uti l_av

23 / 56

g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_1 5

thin k

250 37

Y 200 15. 395

24. 416

9.0 21

182 20. 175

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

即

检

索

链

路

在

大

促

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =87 .32 % gpu _uti l_av g=9

24 / 56

压

测

期

间

出

现

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

根

据

之

前

的

工

具

调

用

和

结

果

，

我

需

要

5.0 0% gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

25 / 56

综

合

所

有

信

息

来

确

定

根

本

原

因

并

提

出

解

决

方

案

。 首

先

，

回

顾

之

前

的

步

骤

，

用

户

已

经

通

过

26 / 56

多

个

工

具

调

用

来

收.. .

Qw en3 -32 B

ste p_1 5

no_ thin k

250 38

Y 200 15. 435

15. 714

0.2 78

4 14. 379

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =87 .30 % gpu _uti l_av g=9 9.2 5% gpu _m

27 / 56

em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_1 8

thin k

278 92

Y 200 14. 492

23. 517

9.0 25

177 19. 612

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

即

检

索

链

路

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =90 .28 %

28 / 56

在

大

促

压

测

期

间

出

现

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

用

户

希

望

我

像

一

个

Re Act 运

维

代

gpu _uti l_av g=8 0.7 5% gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

29 / 56

理

一

样

逐

步

调

查

，

优

先

检

查

运

行

手

册

、

最

近

变

更

、

监

控

、

日

志

、

配

置

、

工

单

和

集

群

事

件

30 / 56

，

再

决

定

下

一

步

。 ...

Qw en3 -32 B

ste p_1 8

no_ thin k

278 94

Y 200 14. 497

14. 782

0.2 84

4 14. 062

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =90 .26 % gpu _uti l_av g=1 00. 00 %

31 / 56

gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

Qw en3 -32 B

ste p_2 0

thin k

294 25

Y 200 8.4 03

17. 443

9.0 40

177 19. 580

129 好

的

，

我

现

在

需

要

处

理

用

户

的

问

题

，

即

检

索

kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =95

32 / 56

链

路

在

大

促

压

测

期

间

出

现

延

迟

抖

动

和

局

部

召

回

空

结

果

的

情

况

。

用

户

希

望

我

像

一

个

Re Act 运

.06 % gpu _uti l_av g=9 0.5 0% gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

33 / 56

维

代

理

一

样

逐

步

调

查

，

优

先

检

查

运

行

手

册

、

最

近

变

更

、

监

控

、

日

志

、

配

置

、

工

单

和

集

群

34 / 56

事

件

，

再

决

定

下

一

步

。 ...

Qw en3 -32 B

ste p_2 0

no_ thin k

294 27

Y 200 7.9 92

8.2 85

0.2 93

4 13. 670

7 OK kv_ cac he= 0.0 0% (bef ore =0. 00 %) pref ix_c ach e_h it_r ate =95 .10 % gpu _uti l_av g=1 00.

35 / 56

Raw Results

复制代码

00 % gpu _m em _uti l_av g=8 6.8 9% gpu _m em _us ed_ tota l_m b=1 138 88

{ "base_url": "http://127.0.0.1:8000/v1", "mode": "bench", "models": [ { "name": "Qwen3-32B", "tokenizer": "/data/models/Qwen3-32B" } ], "context_sizes": [ 128, 512, 2048,

36 / 56

4096, 8192 ], "testset_path": "model_service_testset.json", "thinking_mode": "no_think", "max_tokens": 128, "temperature": 0.0, "timeout": 1800, "first_token_timeout": 60, "environment": { "hostname": "hw-xn-cd-rbt-4-dvznt-001", "platform": "linux", "kernel": "Linux hw-xn-cd-rbt-4-dvznt-001 5.4.0-162-generic #179- Ubuntu SMP Mon Aug 14 08:51:31 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux", "os_release": "Ubuntu 20.04.6 LTS", "python_version": "3.9.5", "cpu_model": "Intel Xeon Processor (Cascadelake)", "cpu_cores_logical": 32, "cpu_cores_physical": 16, "total_memory_gb": 251.88890838623047, "disk_root_total_gb": 245.95623016357422, "disk_root_free_gb": 73.60884475708008, "gpu_summary": [ "GPU 0: Tesla V100S-PCIE-32GB (UUID: GPU-3beb1d35-b4eb-59a5-89d8- c314944a6847)", "GPU 1: Tesla V100S-PCIE-32GB (UUID: GPU-dfefd7fe-ee7c-0a25-2da2- 03666ec762bb)", "GPU 2: Tesla V100S-PCIE-32GB (UUID: GPU-d7586c8e-d0ce-6736-491e- 625344696468)", "GPU 3: Tesla V100S-PCIE-32GB (UUID: GPU-086b7df6-bf85-67f1-13f0- 6e92592ad511)" ], "nvidia_smi_summary": "Tesla V100S-PCIE-32GB, 32768, 535.230.02; Tesla V100S-PCIE-32GB, 32768, 535.230.02; Tesla V100S-PCIE-32GB, 32768, 535.230.02; Tesla V100S-PCIE-32GB, 32768, 535.230.02",

"raw_notes": [ ]

},

"smoke_results": [ ],

37 / 56

"bench_results": [ { "model": "Qwen3-32B", "case_name": "step_3", "thinking_mode": "think", "context_tokens_target": 7790, "success": true, "http_status": 200, "error": "", "ttft_seconds": 9.92360903462395, "total_seconds": 18.976425798609853, "decode_seconds": 9.052816763985902, "raw_chunks": 129, "prompt_tokens_estimated": 7790, "completion_tokens_estimated": 208, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 22.976274172196856, "output_preview": "<think> 好的，我现在需要处理用户的问题，关于检索链 路在大促压测期间出现的延迟抖动和局部召回空结果的情况。用户已经提供了详细的运行手

册检索结果、最近的变更记录以及监控摘要。我的任务是根据这些信息继续调查问题，并按

照ReAct运维...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 6609.0, "prefix_cache_hits": 0.0, "gpu_utilization_avg": 0.0, "gpu_memory_used_mb_total": 113528.0, "gpu_memory_utilization_avg": 86.614990234375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0,

38 / 56

"prefix_cache_queries": 14334.0, "prefix_cache_hits": 0.0, "gpu_utilization_avg": 86.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_3", "thinking_mode": "no_think", "context_tokens_target": 7791, "success": true, "http_status": 200, "error": "", "ttft_seconds": 9.919018597807735, "total_seconds": 10.277193604037166, "decode_seconds": 0.35817500622943044, "raw_chunks": 8, "prompt_tokens_estimated": 7791, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 11.167725079727601, "output_preview": "<think> </think> OK。", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 14334.0, "prefix_cache_hits": 0.0, "gpu_utilization_avg": 86.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

39 / 56

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 22063.0, "prefix_cache_hits": 48.0, "gpu_utilization_avg": 88.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_5", "thinking_mode": "think", "context_tokens_target": 11507, "success": true, "http_status": 200, "error": "", "ttft_seconds": 10.06434306083247, "total_seconds": 19.163893630728126, "decode_seconds": 9.099550569895655, "raw_chunks": 129, "prompt_tokens_estimated": 11507, "completion_tokens_estimated": 200, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 21.97910748050202, "output_preview": "<think> 好的，我现在需要处理用户关于检索链路在大促 压测期间出现延迟抖动和局部召回空结果的问题。根据之前的ReAct运维代理的执行历史， 已经完成了运行手册检索、最近变更检查、监控摘要分析、关键错误日志查看以及配置差异

对比。现在需...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0,

40 / 56

"prefix_cache_queries": 22063.0, "prefix_cache_hits": 48.0, "gpu_utilization_avg": 88.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 34018.0, "prefix_cache_hits": 7760.0, "gpu_utilization_avg": 84.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_5", "thinking_mode": "no_think", "context_tokens_target": 11509, "success": true, "http_status": 200, "error": "", "ttft_seconds": 9.912894133944064, "total_seconds": 10.197755597997457, "decode_seconds": 0.2848614640533924, "raw_chunks": 7, "prompt_tokens_estimated": 11509, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.04191336758091,

41 / 56

"output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 34018.0, "prefix_cache_hits": 7760.0, "gpu_utilization_avg": 67.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 45977.0, "prefix_cache_hits": 15472.0, "gpu_utilization_avg": 95.25, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_8", "thinking_mode": "think", "context_tokens_target": 16739, "success": true, "http_status": 200, "error": "", "ttft_seconds": 15.577955306041986, "total_seconds": 24.641841178759933, "decode_seconds": 9.063885872717947, "raw_chunks": 129, "prompt_tokens_estimated": 16739,

42 / 56

"completion_tokens_estimated": 195, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 21.51395138225921, "output_preview": "<think> 好的，我现在需要处理用户关于检索链路在大促 压测期间出现延迟抖动和局部召回空结果的问题。根据之前的对话历史，用户已经进行了多

个步骤的排查，包括运行手册检索、最近变更检查、监控摘要分析、错误日志查看、配置差

异对比、工单反馈...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 45977.0, "prefix_cache_hits": 15472.0, "gpu_utilization_avg": 89.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 62841.0, "prefix_cache_hits": 27408.0, "gpu_utilization_avg": 91.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_8", "thinking_mode": "no_think", "context_tokens_target": 16740,

43 / 56

"success": true, "http_status": 200, "error": "", "ttft_seconds": 15.64940412901342, "total_seconds": 15.932977526914328, "decode_seconds": 0.2835733979009092, "raw_chunks": 7, "prompt_tokens_estimated": 16740, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.10569549051193, "output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 62841.0, "prefix_cache_hits": 27408.0, "gpu_utilization_avg": 84.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 79709.0, "prefix_cache_hits": 39344.0, "gpu_utilization_avg": 94.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} },

44 / 56

{ "model": "Qwen3-32B", "case_name": "step_10", "thinking_mode": "think", "context_tokens_target": 19385, "success": true, "http_status": 200, "error": "", "ttft_seconds": 11.132444838061929, "total_seconds": 20.19700059387833, "decode_seconds": 9.0645557558164, "raw_chunks": 129, "prompt_tokens_estimated": 19385, "completion_tokens_estimated": 181, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 19.967884237885436, "output_preview": "<think> 好的，我现在需要处理用户的问题，即检索链路 在大促压测期间出现延迟抖动和局部召回空结果的情况。用户希望我像一个ReAct运维代理 一样逐步调查，优先检查运行手册、最近变更、监控、日志、配置、工单和集群事件，再决

定下一步。 ...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 79709.0, "prefix_cache_hits": 39344.0, "gpu_utilization_avg": 94.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 99425.0, "prefix_cache_hits": 56192.0,

45 / 56

"gpu_utilization_avg": 92.75, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_10", "thinking_mode": "no_think", "context_tokens_target": 19386, "success": true, "http_status": 200, "error": "", "ttft_seconds": 11.157778067979962, "total_seconds": 11.440452800132334, "decode_seconds": 0.2826747321523726, "raw_chunks": 7, "prompt_tokens_estimated": 19386, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.150539630984229, "output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 99425.0, "prefix_cache_hits": 56192.0, "gpu_utilization_avg": 82.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": {

46 / 56

"collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 119145.0, "prefix_cache_hits": 73040.0, "gpu_utilization_avg": 94.25, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_12", "thinking_mode": "think", "context_tokens_target": 21650, "success": true, "http_status": 200, "error": "", "ttft_seconds": 10.012921478133649, "total_seconds": 19.066865098197013, "decode_seconds": 9.053943620063365, "raw_chunks": 129, "prompt_tokens_estimated": 21650, "completion_tokens_estimated": 190, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 20.98533058886778, "output_preview": "<think> 好的，我现在需要处理用户的问题，即检索链路 在大促压测期间出现延迟抖动和局部召回空结果的情况。根据之前的工具调用和结果，我需

要综合所有信息来确定根本原因并提出解决方案。 首先，回顾之前的步骤，用户已经通过 多个工具调用来收...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 119145.0, "prefix_cache_hits": 73040.0,

47 / 56

"gpu_utilization_avg": 93.75, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 141069.0, "prefix_cache_hits": 92736.0, "gpu_utilization_avg": 93.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_12", "thinking_mode": "no_think", "context_tokens_target": 21651, "success": true, "http_status": 200, "error": "", "ttft_seconds": 10.017023098189384, "total_seconds": 10.296626781113446, "decode_seconds": 0.279603682924062, "raw_chunks": 7, "prompt_tokens_estimated": 21651, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.305963205378687, "output_preview": "<think> </think> OK", "metrics_before": {

48 / 56

"collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 141069.0, "prefix_cache_hits": 92736.0, "gpu_utilization_avg": 86.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 162997.0, "prefix_cache_hits": 112432.0, "gpu_utilization_avg": 100.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_15", "thinking_mode": "think", "context_tokens_target": 25037, "success": true, "http_status": 200, "error": "", "ttft_seconds": 15.394691379275173, "total_seconds": 24.415882788132876, "decode_seconds": 9.021191408857703, "raw_chunks": 129, "prompt_tokens_estimated": 25037, "completion_tokens_estimated": 182, "prompt_tokens_reported": null,

49 / 56

"completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 20.174718809457733, "output_preview": "<think> 好的，我现在需要处理用户的问题，即检索链路 在大促压测期间出现延迟抖动和局部召回空结果的情况。根据之前的工具调用和结果，我需

要综合所有信息来确定根本原因并提出解决方案。 首先，回顾之前的步骤，用户已经通过 多个工具调用来收...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 162997.0, "prefix_cache_hits": 112432.0, "gpu_utilization_avg": 94.25, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 188083.0, "prefix_cache_hits": 134336.0, "gpu_utilization_avg": 95.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_15", "thinking_mode": "no_think", "context_tokens_target": 25038, "success": true, "http_status": 200,

50 / 56

"error": "", "ttft_seconds": 15.435323198325932, "total_seconds": 15.713504565414041, "decode_seconds": 0.27818136708810925, "raw_chunks": 7, "prompt_tokens_estimated": 25038, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.379108284175869, "output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 188083.0, "prefix_cache_hits": 134336.0, "gpu_utilization_avg": 85.75, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 213173.0, "prefix_cache_hits": 156240.0, "gpu_utilization_avg": 99.25, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B",

51 / 56

"case_name": "step_18", "thinking_mode": "think", "context_tokens_target": 27892, "success": true, "http_status": 200, "error": "", "ttft_seconds": 14.491989102680236, "total_seconds": 23.517217736691236, "decode_seconds": 9.025228634011, "raw_chunks": 129, "prompt_tokens_estimated": 27892, "completion_tokens_estimated": 177, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 19.611691534659492, "output_preview": "<think> 好的，我现在需要处理用户的问题，即检索链路 在大促压测期间出现延迟抖动和局部召回空结果的情况。用户希望我像一个ReAct运维代理 一样逐步调查，优先检查运行手册、最近变更、监控、日志、配置、工单和集群事件，再决

定下一步。 ...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 213173.0, "prefix_cache_hits": 156240.0, "gpu_utilization_avg": 98.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 240945.0, "prefix_cache_hits": 181312.0, "gpu_utilization_avg": 80.75, "gpu_memory_used_mb_total": 113888.0,

52 / 56

"gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_18", "thinking_mode": "no_think", "context_tokens_target": 27894, "success": true, "http_status": 200, "error": "", "ttft_seconds": 14.497181296814233, "total_seconds": 14.781645926181227, "decode_seconds": 0.2844646293669939, "raw_chunks": 7, "prompt_tokens_estimated": 27894, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 14.061502158989034, "output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 240945.0, "prefix_cache_hits": 181312.0, "gpu_utilization_avg": 80.75, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi",

53 / 56

"kv_cache_usage_perc": 0.0, "prefix_cache_queries": 268721.0, "prefix_cache_hits": 206384.0, "gpu_utilization_avg": 100.0, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_20", "thinking_mode": "think", "context_tokens_target": 29425, "success": true, "http_status": 200, "error": "", "ttft_seconds": 8.403404055163264, "total_seconds": 17.44319086894393, "decode_seconds": 9.039786813780665, "raw_chunks": 129, "prompt_tokens_estimated": 29425, "completion_tokens_estimated": 177, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 19.580107766498774, "output_preview": "<think> 好的，我现在需要处理用户的问题，即检索链路 在大促压测期间出现延迟抖动和局部召回空结果的情况。用户希望我像一个ReAct运维代理 一样逐步调查，优先检查运行手册、最近变更、监控、日志、配置、工单和集群事件，再决

定下一步。 ...", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 268721.0, "prefix_cache_hits": 206384.0, "gpu_utilization_avg": 100.0, "gpu_memory_used_mb_total": 113888.0,

54 / 56

"gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 297907.0, "prefix_cache_hits": 234128.0, "gpu_utilization_avg": 90.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

} }, { "model": "Qwen3-32B", "case_name": "step_20", "thinking_mode": "no_think", "context_tokens_target": 29427, "success": true, "http_status": 200, "error": "", "ttft_seconds": 7.992037368007004, "total_seconds": 8.284638602286577, "decode_seconds": 0.29260123427957296, "raw_chunks": 7, "prompt_tokens_estimated": 29427, "completion_tokens_estimated": 4, "prompt_tokens_reported": null, "completion_tokens_reported": null, "total_tokens_reported": null, "output_tps_estimated": 13.670482319900614, "output_preview": "<think> </think> OK", "metrics_before": { "collected": true, "source": "vllm_metrics+nvidia_smi",

55 / 56

"kv_cache_usage_perc": 0.0, "prefix_cache_queries": 297907.0, "prefix_cache_hits": 234128.0, "gpu_utilization_avg": 90.5, "gpu_memory_used_mb_total": 113888.0, "gpu_memory_utilization_avg": 86.8896484375,

"raw_notes": [ ]

}, "metrics_after": { "collected": true, "source": "vllm_metrics+nvidia_smi", "kv_cache_usage_perc": 0.0, "prefix_cache_queries": 327097.0,

"prefix cache hits": 261888 0

56 / 56