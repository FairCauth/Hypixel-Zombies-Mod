# 灵动岛 Skia HUD 与 Demo

## 真实功能接入

默认「内容来源 / Source」为 Live。QQ 音乐来自 Windows 的媒体会话：曲名、歌手、封面、播放/暂停与可用进度，QQ 未运行或未发布媒体会话时隐藏音乐任务。展开始终显示进度条，有时长时显示真实进度与时间。`qqmusic-timeline.cs` 在独立后台线程通过 Windows UI Automation 每 500ms 读取 QQ 播放栏的当前时间与总时长，匹配且新鲜的播放栏读数优先，无法读取时回退系统媒体时间轴。枚举 QQ 所属可见顶层窗口并校验曲名和歌手，避免 `MainWindowHandle` 指向桌面歌词或隐藏同名窗口；按附近共同容器和基线定位时间，兼容额外的容器层级。换歌清除旧数据并保留仍有效的时间控件；冻结或过期的读数不继续用于进度。最小化时也尝试定位，但 QQ 必须提供可访问数据，若首次定位失败需打开一次主窗口；不会自动聚焦、恢复窗口或修改播放。两种来源都不可用时使用未知进度样式和 `--:--`，不模拟歌曲播放进度。后台单进程每 500ms 读取媒体信息，歌曲未改变时复用封面；F9 控制播放/暂停，F10 下一首，Shift+F10 上一首，只对选中的 QQ 会话发送控制。

同一后台进程中的 `qqmusic-audio.cs` 通过 Core Audio 枚举所有活动输出设备的音频会话，只读取 QQMusic 进程的音量峰值，约 30Hz 更新。五根条显示最近音量历史，用不同采样延迟拉开条高差异，扩大量差而非把音量压近满格；弱音增益、实际音量上升增强、22ms 起跳和 85ms 回落使节奏更明显，条高范围为 2.5–26.5 像素。暂停/静音/采样不可用时停止跳动；它是节奏音量可视化，未伪装为频谱。封面在后台抽样提取两个色调，音乐条与进度条使用渐变，换歌平滑过渡；没有封面时使用柔和中性色。原演示任务保留模拟动画，Live 使用真实音量。

「显示歌词 / Show Lyrics」默认关闭。开启后优先读取 QQ 播放栏独立歌词区域，不依赖桌面歌词；不把曲名、时间、评论计数或列表文字当成歌词。选项开启、真实时长可用时，`QQMusicLyrics` 在后台按曲名、歌手、时长查询 LRCLIB，为缺失歌词或原生歌词的匹配计时提供同步 LRC。核验返回曲名、歌手及不超过 2 秒的时长差异；原生文字与同步句子忽略标点、空格、大小写后匹配时附加该时间区间；带逐字时间戳时使用对应同步原文以保证文字偏移一致。原文加译文的合并标签继续使用整句区间，不将一个原文的字词偏移错用到合并文本；无法匹配时回退已验证版本的同步句子。支持小数/重复时间戳、offset、空白间奏、暂停与跳转，最后一句以真实歌曲结尾为边界。缓存最多 16 首，失败至少 2 分钟后重试，连接/总超时 3/6 秒，关闭和换歌立即隐藏旧句。紧凑状态 220ms 换句淡入，唱过部分以柔和封面色高光，未唱部分浅灰；增强 LRC 的字词时间标签保留为 Unicode 码点偏移，offset 与重复段落同步移动字词时间，Skia 根据实际字形宽度定位高光；结束时间标签保留句间停顿，乱序或越界标签退回句内插值。普通 LRC 仍只有整句插值，不能保证每字与演唱精确同步。「高光提前 (ms)」默认 250ms，范围 -2000–2000ms，正数提前、负数延后；只影响高光及跟随滚动，不调整真实时间轴或换句，也不在句子开始之前预先唱亮下一句。长句随演唱边界滚动，无时间戳时保留往返滚动，暂停保持位置。岛宽以实际字体测量加 86 像素内容留白为目标，弹簧平滑变化，单任务 138–280、双任务 124–232 岛内逻辑像素，上层 HUD 保持按屏幕适配；展开固定 350 像素。无有效歌词回退曲名和常规宽度。歌词重试不清掉有效进度。

Wave Display 和 Revive Aura 必须启用各自模块才会提供真实数据。音乐与 Wave 同时进行时音乐为主胶囊，Wave 为圆形副岛：Wave 岛仅在最后三秒倒计时开始时出现，等待期间和清场时隐藏，结束后完成数字淡出再退场；中心显示 3、2、1，数字切换带模糊、缩放和弹性落定，结束后淡出；琥珀色圆环按当前实际出波间隔显示剩余比例，首波从回合开始计时，后续各波重新计时，清场时中心留空。原 Wave HUD 保留。复活岛只展示 Revive Aura 实际发包后记录的队友，区分等待服务器确认和已开始复活。新救援自动提升至优先级 100 并展开 3.2 秒，随后音乐 50、救援 45、波次 40，因此救援占副岛、波次保持后台更新。绿色救援环显示进度最大的已确认队友的真实进度；待确认时不填充进度。救援详情和展开音乐底部保留 Wave 摘要，关闭歌词时紧凑音乐也显示回合及秒数。没有音乐时救援为主岛、Wave 为副岛；救援结束恢复音乐 + 波次倒计时副岛。F8/Shift+F8 切换主/副岛常驻展开；下一波倒计时保持收起，手动展开可查看详情，新救援仍可自动提醒。按住 Tab 时尊重所选任务，「自动短暂展开」关闭后不自动展开。完成某个队友不会再次提醒其余旧任务，新加入队友才会触发救援提示。

按住 Tab 临时展开主岛，Shift+Tab 展开副岛，松开 Tab 收起；按住期间不会被自动展开计时结束打断。菜单、F1 和窗口失焦会结束临时展开，按键状态每帧读取以避免遗漏松开事件。F8 仍可切换常驻展开。紧凑状态的中英文与数字根据字形实际轮廓垂直居中，与封面和图标对齐。

波次和救援接入可独立关闭；原 HUD 在接入时隐藏，未接入或灵动岛不可用时恢复。F1 和菜单隐藏岛，退出世界和退出游戏清理进程及 Skia 资源。没有任何真实活动时不画空胶囊。Live 不会产生演示任务或模拟曲目。

下文游戏内示例操作仅用于 Source = Demo；「独立预览」始终为单独的 Demo。

## 游戏内 HUD

「灵动岛 / Dynamic Island」模块默认开启，进入世界后在屏幕顶部居中显示，初始为紧凑状态。**F10** 开关第二个示例任务，**F8** 展开/收起主任务，**Shift+F8** 展开/收起第二任务，**F9** 切换主任务的音乐、计时器和来电示例。右 Shift 配置界面提供大小、顶部距离、两个任务内容、展开目标和自动演示设置。按 F1 隐藏 HUD 或打开菜单时暂时隐藏；退出世界和禁用模块时释放 Skia 资源。

双任务参考 Apple 的 minimal 布局：主胶囊显示精简内容，右侧增加独立圆形或小胶囊，计时器使用较宽的小胶囊显示时间。副岛先开始吸附，在吸附弹簧达到 40% 时启动详情展开，合并和展开自然重叠；另一个任务继续运行。靠近时通过平滑距离场绘制轻微的玻璃连接颈，单岛、分离双岛和合并状态都使用同一套外壳、描边、高光与折射，避免材质切换和重叠边线。详情展开时副岛向圆角内部滑入，收起先压回紧凑胶囊，再断开恢复双岛。任务结束后剩余任务恢复单岛紧凑布局。分离、合并、任务切换都保留弹簧、内容模糊和缩放过渡，动画中途反向或结束任务会取消待展开阶段。

任务通过独立 ID 保存播放、计时与暂停状态；同类任务也可同时运行。优先级高的任务优先显示，同优先级按加入顺序保持位置稳定，最多展示两个任务；其他任务继续运行，空出位置后自动补位。这是项目的调度规则。计时器归零自动结束，关闭或结束按钮只结束所属任务，重复配置不会重置进度或复活已结束任务。

HUD 只绘制岛与阴影，不清屏、不画背景、不拦截移动/攻击。尺寸与位置使用 GUI 逻辑像素，适配 GUI 缩放和窗口大小；小窗口会限制展开尺寸，避免越界。

外壳是透明液态玻璃：复用项目的 Skia RuntimeEffect，采样当前帧不可变的游戏快照，叠加轻度模糊、凸透镜折射、边缘色散、细高光与柔和阴影。玻璃形状跟随同一组宽高弹簧，展开/收起期间内容模糊独立工作；文字带轻微阴影，帮助在明亮背景上保持可读。背景模糊复用现有帧缓存，GUI 与岛自身的缩放、位移分别映射回真实帧坐标。未提供背景快照的独立预览使用半透明渐变与边缘高光。

所有液态玻璃 HUD 共用 `LiquidGlassShader.DEFAULT_EDGE_DISPERSION`：灵动岛、队友、波次、道具、复活和 LR 队列面板都使用同一彩色边缘强度。低饱和度的青紫粉金反光限制在 2.4–3.2 个局部逻辑像素的窄边缘内，并随受光方向变化，避免形成等强度的彩虹描边；RGB 偏移按逻辑像素映射到游戏快照，避免分辨率越高色散越宽。面板中心保持原有通透材质。

## 独立预览

在右 Shift 配置界面选择「灵动岛 / Dynamic Island」，点击「独立预览 / Open Preview」打开。

| 操作 | 效果 |
| --- | --- |
| 点击 / 长按任一岛 | 展开对应任务；长按 0.35 秒后松开保持展开 |
| Space / Shift+Space | 展开收起当前任务 / 展开第二任务 |
| M / Second activity | 添加或移除第二任务 |
| End focused activity | 结束展开任务；未展开时结束主任务 |
| 1 / 2 / 3 | 主任务切换为音乐 / 计时器 / 来电 |
| A / Auto play | 24 秒循环展示单岛、双岛、两个任务分别展开及结束补位 |
| S / Speed | 0.35 倍慢放，便于查看模糊和缩放 |
| 音乐播放控件 | 暂停 / 播放 / 前后跳转 15 秒 |
| 计时器暂停 / 关闭 | 暂停 / 继续；关闭结束对应计时器 |
| 来电按钮 | 结束对应来电示例，不接入真实通话 |
| Esc / F8 | 返回之前的界面 |

动画是参考 Apple 灵动岛的独立实现：透明胶囊、弹性宽高形变、连续反向、内容模糊与缩放、紧凑/展开内容交叉淡化、不同活动切换。外壳与内容分层，模糊不会影响外壳边缘。音乐、来电和计时均为本地示例数据。

`DynamicIsland` 仅依赖 Skija；`DynamicIslandHudRenderer` 将它绘制在透明的顶部 HUD 中，`DynamicIslandHud` 连接游戏事件与设置。`startTask(id, activity, priority)` 添加演示任务，`updateLiveTask(id, IslandLiveData, priority)` 提交真实任务快照，`endTask(id)` 结束任务，`expandTask(id)` 展开可见任务，`setExpanded(false)` 收起。`IslandLiveController` 管理真实任务与短暂提示；`QQMusicBridge` 只在后台读取系统媒体会话，不访问游戏或 Skia。这些绘制/调度方法均由渲染线程调用。`DynamicIslandDemoScene` 保留独立示例与交互控件。

## 离线预览

使用 Java 25 与项目已有 Skija 依赖编译 `DynamicIslandPreview.java`，连同 `src/client/java/com/example/client/skia/render/dynamicisland/*.java` 和 `src/client/java/com/example/client/skia/render/LiquidGlassShader.java`，无需 Minecraft。运行时需包含 `skija-shared:0.116.4`、`skija-windows-x64:0.116.4` 和 `types:0.1.1`。

预览程序参数是输出目录，默认 `build/dynamic-island-preview`；导出 30 FPS 的 720 张 PNG、三种展开状态、双岛和收起中的模糊画面。它还检查弹簧在 30 / 144 FPS 下的一致性、动画中途反向、快速切换、Canvas 栈平衡与缩放后的点击。

导出帧后，使用装有 Pillow 的 Python 执行 `python tools/dynamic-island/make-preview.py`，将实际 Skia 帧打包为 `build/dynamic-island-preview/dynamic-island.gif`。

同样的依赖可编译并运行 `DynamicIslandHudPreview.java`。它导出透明 HUD 帧到 `build/dynamic-island-preview/hud`，检查顶部居中、GUI 缩放、小窗口、连续打断、自动演示、Canvas 状态恢复和原有背景保留。这是离线渲染检查，不代替游戏窗口内的验证。

`DynamicIslandGlassPreview.java` 运行实际玻璃着色器，检查带位移与缩放的背景采样、折射、动画打断和快照所有权，输出到 `build/dynamic-island-preview/glass`。可选参数为带 31 像素标题栏的游戏截图路径；输出的 `game-*-preview.png` 是在提供的截图上离线渲染的材质预览，不是游戏窗口实测。

该程序还检查深色、灰色、亮色背景下不同尺寸玻璃的彩边可见性、中央颜色保留和 1/2/3 倍缩放。`global-colored-edges.png` 每行依次为无色散对照、全局面板、紧凑与展开外壳。

`DynamicIslandMultitaskPreview.java` 检查独立任务进度、同类双任务、优先级抢占及补位、任务归零、第二任务点击与长按、独立暂停和关闭、连续分离/合并打断、重复配置和小窗口。输出到 `build/dynamic-island-preview/multitask`。可选参数同样是带 31 像素标题栏的游戏截图路径，输出 `two-tasks.png`、`second-expanded.png`、`restored-dual.png`、`task-ended.png`；这些是实际 Skia 在截图上的离线渲染。

`DynamicIslandDockingPreview.java` 专门检查吸附与展开的时间重叠、分离和完全并入状态下边框的像素一致性（带背景和无背景）、连接颈的像素覆盖、收起后才分离、中途反向、合并时结束任务和 30/144 FPS 一致性。提供游戏截图参数时导出 60 FPS 的 210 帧到 `build/dynamic-island-preview/docking/frames`；执行 `python tools/dynamic-island/make-docking-preview.py` 打包为 `docking/docking.gif`。此动图在提供的截图上使用实际 Skia 材质离线渲染，不能代替游戏窗口验证。

`QQMusicBridgeCheck.java` 加入 Gson 和 `src/client/resources` 类路径后验证真实 QQ 曲目、封面、持续刷新、缓存、其他播放器过滤、无效数据与线程关闭，运行参数为输出目录 `build/dynamic-island-preview/live`。它只读取，不改变播放。导出的 `qq-session.json` 是本地验证数据，不打进 jar。`DynamicIslandLivePreview.java` 使用这份真实音乐快照和明确模拟的游戏任务，验证抢占、补位、关闭模块、自动展开/收起、手动优先及空状态；传入原游戏截图后输出音乐、波次、救援的实际 Skia 预览。QQ 读取已在当前 Windows 会话验证；波次、救援需在 Hypixel 实际游戏内继续验证。

`DynamicIslandLivePreview.java` 也检查按住 Tab 超过自动提示时限、Shift+Tab、任务到达/结束/抢占、松开时反转未完成的合并以及常驻展开兼容性。`DynamicIslandCompactPreview.java` 按实际 Skia 像素检查中文、英文、混合文字和长标题省略后是否与封面/图标中心对齐，输出到 `build/dynamic-island-preview/compact`。

`DynamicIslandMusicVisualsPreview.java` 用明确模拟的音量与时间快照检查静音不会产生假跳动、音量响应、实际音量下降后的收缩与节拍起跳、不同采样延迟的条高差、弱音响应、暂停、采样不可用、封面提色、真实/未知进度布局与移除底部提示，输出到 `build/dynamic-island-preview/music-visuals`。QQ 读取检查同时验证音量包不会清除曲目信息、音量独立刷新和关闭后清理。

`QQMusicPlaybackClock` 使用单调时钟处理 1 秒精度的播放栏区间、系统样本年龄和读取延迟。重复/旧版本样本不重置计时，误差校正速度限制为播放速率的 ±8%，大幅异常和真实向后跳转须连续两次读数一致才重置，暂停/换歌独立处理。未知时间短暂丢失允许已确认时钟延续最多 3 秒。HUD 每帧冻结一个 Music 快照，歌词选择、高光与展开进度条都使用它。`QQMusicPlaybackClockCheck.java` 完全离线、可控时钟检查 120 秒整秒采样与 30 秒精确采样的抖动、持续单调性、异常包/旧包、前后拖动、暂停/恢复、倍速、封面延迟、来源切换、换歌与歌词共用帧时间。`QQMusicTimelineCheck fixtures` 另外检查生产 Bridge 的 JSON 处理、短暂缺失与超过 3 秒的缺失，以及已有歌词开关逻辑；不会启动原生 QQ 读取或歌词网络查询。

`QQMusicTimelineCheck.java` 读取当前真实 QQ 播放时间及歌词并保存快照，再通过明确注入的数据检查暂停保持、播放插值、向后跳转、换歌、未知时间及独立音量包，并检查歌词开关、换歌清理、未知进度时的歌词、过长数据与空闲媒体。`watch` 参数连续观察两分钟，`require-lyrics` 必须取得实际歌词才能通过。当前新 jar 已实测读取 `Life Is Beautiful / The Afters` 的 `82 / 232` 秒与同步歌词，开关关闭再开启可恢复。`QQMusicNativeCheck.ps1` 对生产 C# 代码检查多歌手空格差异、全角字符、错误版本/歌手拒绝、时钟和评论计数过滤。首次最小化时能否定位取决于 QQ 可访问性数据。Skia 截图预览不能替代重启后游戏内验证。

`QQMusicTimelineCheck require-lyrics observe` 另外连续观察 30 秒，每 500ms 验证当前真实时间轴与歌词仍可用，并检查未拖动时的播放进度不会回退。此检查适合保持当前歌曲在有歌词的暂停位置，不切歌；间奏无歌词、用户关闭 QQ 或主动换歌均可能使其断言失败。

`QQMusicLyricsCheck.java` 离线检查同步歌词解析、严格匹配、时长差异、间奏清空、跳转与数据限制；传入 `live` 只读验证 LRCLIB 返回歌词、暂停、关闭、重新开启时的缓存、换歌隔离与关闭清理。

`QQMusicLyricAlignmentCheck.java` 通过不启动后台线程的生产 Bridge 注入已匹配歌曲的同步歌词，检查原文/译文/合并文本、同时间戳空行、全角与零宽字符、QQ 省略文本、相邻句 1 秒采样偏差、重复副歌及跳转、过期与错误歌曲文本、增强 LRC 时间标签和缺失数据回退。整句连续 240 帧切换不同原生文本时，高光不能消失或回退，读取歌词不能改变音乐进度。传入 `render` 使用实际 Bridge 输出和 Skia 检查双语及文本不匹配时的高光像素，输出 `lyrics/aligned-bilingual-*.png` 与 `lyrics/aligned-fallback-*.png`。没有同步歌词时仍只显示 QQ 原生文本，不虚构高光时间；此程序不连接 QQ 或歌词服务。另检查逐字分段和句间停顿、码点偏移、重复段落/offset、乱序回退、独立正负提前量、暂停和 480 帧连续性；render 检查相同暂停进度下 250ms 提前确实改变 Skia 高光像素，输出 word-advance-*.png。

`DynamicIslandLyricsPreview.java` 用实际 Skia 像素检查歌词开关、缺失/错误歌曲/过期歌词回退、中文居中、高光、暂停和回退进度、宽度弹性变化与单双任务上限、长句跟随高光滚动和普通歌词滚动裁切、展开宽度。封面及音乐条不受文字影响。输出到 `build/dynamic-island-preview/lyrics`。`DynamicIslandWaveMusicPreview lyrics` 使用保存的封面和游戏背景、明确注入的歌词与时间戳生成 210 帧生产渲染预览；用 `make-wave-music-preview.py build/dynamic-island-preview/lyrics-motion` 打包为 `lyrics.gif`，演示高光、长短句伸缩与 Wave 副岛共存，不代表实际游戏录屏或真实歌词读取。

`DynamicIslandWaveCountdownCheck.java` 检查首波与不等长出波间隔、清场及无数据、圆环像素随剩余时间递减、最后三秒的数字、切换动效、结束淡出、重复更新不打断动画、圆环不受数字模糊影响、倒计时保持收起、点击/按住、合并反转及音乐退出补位。输出到 `build/dynamic-island-preview/wave-countdown`。

`DynamicIslandRescueRingCheck.java` 检查三任务时音乐主岛 + 救援副岛、Wave 数据保留、点击救援圆环展开、圆环填充随已确认进度增加及待确认不填充。输出到 `build/dynamic-island-preview/rescue-ring`。

`DynamicIslandWaveMusicPreview.java` 从 `live/background.png` 与已保存的 `live/qq-session.json` 读取背景和音乐信息，使用明确模拟的波次、播放时间及音量，在生产 HUD 中导出音乐主岛 + 倒计时圆环副岛、Shift+Tab 展开 Wave、Tab 展开 Music 和收起动画。输出到 `build/dynamic-island-preview/wave-music-round`，包含完整游戏截图与 210 张顶部细节帧。运行 `make-wave-music-preview.py` 打包成 7 秒 `wave-music.gif`。传入 `rescue` 参数则导出三任务场景：新救援自动展开、两位队友独立进度、救援退到副岛、Shift+Tab 再次查看、结束后恢复波次倒计时。输出到 `build/dynamic-island-preview/rescue-triple`，共 330 帧；打包程序传入此目录得到 11 秒 `rescue-triple.gif`。这些程序不读取或控制正在运行的 QQ 与 Minecraft。

传入 countdown 参数可渲染保持收起的 3→2→1 数字动效，包含模糊、缩放、弹性落定与结束淡出。输出到 build/dynamic-island-preview/wave-countdown-motion；打包程序传入此目录得到 wave-countdown.gif。时间、波次及音量为模拟数据，使用实际生产 Skia 渲染器。

Live 调度检查还通过可控单调时钟验证音乐暂停 30 秒后移除、Wave/Revive 补位、再次播放即时恢复和下一次暂停重新计时。不传截图参数时只执行调度检查。
