# D3关系图（V3版本）
 目前采用力导图来布局，好处在于可以不用过分在乎节点位置，
 也能自动让节点看上去更加舒服、不紧凑

## graph.js
主要逻辑在graph.js里面：
- 包含新增节点和连接线
    1. addNode(nodeArrays,linkArrays)，必须是数组否则无效
- 包含动态加载URL得到JSON数据得到新的图
    1. updateGraphURL('relation.json')
- 包含动态加载JSON得到新的图
    1. updateGraphJSON(jsonObject)
- 添加线，线的文字，节点图，节点文字四个元素，这四个元素会根据力导图的物理模型进行TICK的计算
- 有向图线的箭头marker，决定展示出来的是有向图还是无向图的关键因素
- 增加图标的点击等等事件，目前给出点击菜单配置
- 目前对于力导图的物理模型增加了摩擦力设置friction(0.6)和gravity(0.08)保证TICK效果不会过分弹（增加刚性并不能有效解决）

## force
由于都是采用FORCE力导图，则简单说下力导图的原理。
节点直接有斥力，连接线会给一个拉力，整体物理有摩擦力和引力（引力会从四周往中心），
从而保持整体最终的平衡。（这样节点直接肯定不会重合，线由于弹力，摩擦力和引力的作用交错的情况也会尽量减少）

d3.layout.force()有三个on事件,start,tick,end。分别是开始计算，每一次的跳动计算，计算完成。
d3巧妙的通过四叉数的原理，来让tick的时间复杂度降低。

所以假如要新增元素，需要在tick方法下进行元素的计算，否则新增的元素无法按照力导图的物理模型进行位置的运算。

更多的force的API详见 [https://github.com/d3/d3/blob/master/API.md#forces-d3-force]
