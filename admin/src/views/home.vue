<template>
  <div class="home-content">
    <div id="home-title" class="home-title animate__animated">
      <div class="titles" >
        <span>欢迎使用</span>
        {{this.$project.projectName}}
      </div>
    </div>
    <div id="user-box" class="user-box animate__animated">
      <div class="user-top-box">
        <el-image class="avatar" :src="avatar?this.$base.url + avatar : require('@/assets/img/avator.png')"></el-image>
        <div class="user-top-item">
          <div class="nickname">
            <span>用户名</span>
            {{this.$storage.get('adminName')}}
          </div>
          <div class="role">
            <span>角色</span>
            {{this.$storage.get('role')}}
          </div>
        </div>
      </div>
      <div class="user-bottom-box">
        <div class="ip">
          <span>上次登录地址：</span>
          <span>{{locationIp?locationIp:'首次登录'}}</span>
        </div>
        <div class="time">
          <span>上次登录时间：</span>
          <span>{{locationTime?locationTime:'首次登录'}}</span>
        </div>
      </div>
    </div>
    <div class="statis-box">
      <div id="statis1" class="statis1 animate__animated" v-if="isAuth('chezhu','首页总数')">
        <div class="left">
          <span class="icon iconfont icon-zhangjie8"></span>
        </div>
        <div class="right">
          <div class="num">{{chezhuCount}}</div>
          <div class="name">会员总数</div>
        </div>
      </div>
      <div id="statis2" class="statis2 animate__animated" v-if="isAuth('weixiujishi','首页总数')">
        <div class="left">
          <span class="icon iconfont icon-zhangjie8"></span>
        </div>
        <div class="right">
          <div class="num">{{weixiujishiCount}}</div>
	          <div class="name">今日预约</div>
        </div>
      </div>
      <div id="statis3" class="statis3 animate__animated" v-if="isAuth('weixiujilu','首页总数')">
        <div class="left">
          <span class="icon iconfont icon-zhangjie8"></span>
        </div>
        <div class="right">
          <div class="num">{{weixiujiluCount}}</div>
	          <div class="name">今日完成</div>
        </div>
      </div>
    </div>

    <div class="type3">
      <div id="chezhuChart1" class="echarts1 animate__animated" v-if="isAuth('chezhu','首页统计')"></div>
      <div id="weixiujishiChart1" class="echarts2 animate__animated" v-if="isAuth('weixiujishi','首页统计')"></div>

      <div class="echarts3 animate__animated" v-if="isAuth('weixiujilu','首页统计')" style="display: flex; flex-direction: column;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; padding: 0 10px;">
          <span style="font-weight: 600; font-size: 16px; color: #333;">
	            <i class="el-icon-s-data" style="color: #c4937d; margin-right: 5px;"></i>近14天服务趋势
          </span>
        </div>
        <div id="weixiujiluChart1" style="width: 100%; flex: 1;"></div>
      </div>

    </div>
  </div>
</template>

<script>
import 'animate.css'
import router from '@/router/router-static'
import * as echarts from 'echarts'

export default {
  data() {
    return {
      chezhuCount: 0,
      weixiujishiCount: 0,
      weixiujiluCount: 0,
      dateRange: [], // 核心参数：用于绑定用户选择的时间范围
      // 保留原有的图表配置属性以防其他地方调用报错
      boardBase: {"funnelNum":8,"lineNum":8,"gaugeNum":8,"barNum":8,"pieNum":8},
      pie: {"backgroundColor":"transparent","color":["#a84f64","#c4937d","#7f8f85","#d4b7aa","#75606b"],"title":{"textStyle":{"color":"#f3f5f7","fontSize":14}},"tooltip":{}},
    };
  },
	  mounted(){
	    this.init();
	    this.loadDashboard();

    window.addEventListener('scroll', this.handleScroll)
    setTimeout(()=>{
      this.handleScroll()
    },100)
  },
  computed: {
    avatar(){
      return this.$storage.get('headportrait')?this.$storage.get('headportrait'):''
    },
    locationIp(){
      return this.$storage.get('beforeLocation')?this.$storage.get('beforeLocation'):''
    },
    locationTime(){
      return this.$storage.get('beforeTime')?this.$storage.get('beforeTime'):''
    },
  },
  methods:{
    handleScroll() {
      let arr = [
        {id:'home-title',css:'animate__bounceInUp'},
        {id:'user-box',css:'animate__bounceInUp'},
        {id:'statis1',css:'animate__bounceInUp'},
        {id:'statis2',css:'animate__bounceInUp'},
        {id:'statis3',css:'animate__bounceInUp'},
        {id:'chezhuChart1',css:'animate__bounceInUp'},
        {id:'weixiujishiChart1',css:'animate__bounceInUp'},
        {id:'weixiujiluChart1',css:'animate__bounceInUp'},
      ]
      for (let i in arr) {
        let doc = document.getElementById(arr[i].id)
        if (doc) {
          let top = doc.offsetTop
          let win_top = window.innerHeight + window.pageYOffset
          if (win_top > top && doc.classList.value.indexOf(arr[i].css) < 0) {
            doc.classList.add(arr[i].css)
          }
        }
      }
    },
	    init(){
	      if(!this.$storage.get('Token')){
	        router.push({ name: 'login' })
	      }
	    },

	    loadDashboard() {
	      const endpoint = this.$storage.get('apiRole') === 'BEAUTICIAN'
	        ? 'api/v1/management/technician-dashboard'
	        : 'api/v1/management/dashboard'
	      this.$http({ url: endpoint, method: 'get' }).then(({ data }) => {
	        if (!data || data.code !== 0) return
	        const dashboard = data.data || {}
	        const summary = dashboard.summary || {}
	        this.chezhuCount = summary.memberCount || 0
	        this.weixiujishiCount = summary.todayAppointments || 0
	        this.weixiujiluCount = summary.todayCompleted || 0
	        this.renderDashboardCharts(dashboard)
	      })
	    },

	    renderDashboardCharts(dashboard) {
	      this.$nextTick(() => {
	        const statusEl = document.getElementById('chezhuChart1')
	        if (statusEl) {
	          const chart = echarts.init(statusEl, 'macarons')
		          chart.setOption({
		            backgroundColor: 'transparent',
		            textStyle: { color: '#cdbfc6' },
		            title: { text: '近30天预约状态', left: 'center', textStyle: { color: '#fbf5f7', fontSize: 16, fontWeight: 600 } },
		            tooltip: { trigger: 'item' },
		            legend: { bottom: 0, textStyle: { color: '#cdbfc6' } },
		            series: [{ type: 'pie', radius: ['42%', '68%'], center: ['50%', '52%'], data: dashboard.appointmentStatus || [], itemStyle: { borderColor: '#1b141c', borderWidth: 3 } }],
		            color: ['#c86f8a', '#d7a18c', '#7f9487', '#b99cac', '#80677a']
		          })
	          window.addEventListener('resize', () => chart.resize())
	        }
	        const serviceEl = document.getElementById('weixiujishiChart1')
	        if (serviceEl) {
	          const chart = echarts.init(serviceEl, 'macarons')
	          const rows = dashboard.popularServices || []
		          chart.setOption({
		            backgroundColor: 'transparent',
		            textStyle: { color: '#cdbfc6' },
		            title: { text: '热门美容项目', left: 'center', textStyle: { color: '#fbf5f7', fontSize: 16, fontWeight: 600 } },
		            tooltip: { trigger: 'axis' },
		            grid: { left: '3%', right: '4%', top: 64, bottom: '8%', containLabel: true },
		            xAxis: { type: 'category', data: rows.map(item => item.name), axisLabel: { rotate: 20, color: '#cdbfc6' }, axisLine: { lineStyle: { color: 'rgba(232,203,215,.22)' } }, axisTick: { show: false } },
		            yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#cdbfc6' }, splitLine: { lineStyle: { color: 'rgba(232,203,215,.1)' } } },
		            series: [{ type: 'bar', barMaxWidth: 38, data: rows.map(item => item.value), itemStyle: { color: '#d7a18c', borderRadius: [6, 6, 0, 0] } }]
		          })
	          window.addEventListener('resize', () => chart.resize())
	        }
	        const trendEl = document.getElementById('weixiujiluChart1')
	        if (trendEl) {
	          const chart = echarts.init(trendEl, 'macarons')
	          const rows = dashboard.dailyTrend || []
		          chart.setOption({
		            backgroundColor: 'transparent',
		            textStyle: { color: '#cdbfc6' },
		            tooltip: { trigger: 'axis' },
		            grid: { left: '3%', right: '4%', top: 52, bottom: '8%', containLabel: true },
		            legend: { data: ['预约量', '完成量'], textStyle: { color: '#cdbfc6' } },
		            xAxis: { type: 'category', data: rows.map(item => item.date), axisLabel: { color: '#cdbfc6' }, axisLine: { lineStyle: { color: 'rgba(232,203,215,.22)' } }, axisTick: { show: false } },
		            yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#cdbfc6' }, splitLine: { lineStyle: { color: 'rgba(232,203,215,.1)' } } },
		            series: [
		              { name: '预约量', type: 'line', smooth: true, symbolSize: 7, data: rows.map(item => item.appointmentCount), color: '#d7a18c', areaStyle: { color: 'rgba(215,161,140,.12)' } },
		              { name: '完成量', type: 'line', smooth: true, symbolSize: 7, data: rows.map(item => item.completedCount), color: '#c86f8a' }
		            ]
	          })
	          window.addEventListener('resize', () => chart.resize())
	        }
	      })
	    },

    getchezhuCount() {
      this.$http({ url: `chezhu/count`, method: "get" }).then(({ data }) => {
        if (data && data.code == 0) this.chezhuCount = data.data
      })
    },

    chezhuChat1() {
      this.$nextTick(()=>{
        var chezhuChart1 = echarts.init(document.getElementById("chezhuChart1"),'macarons');
        this.$http({
          url: "chezhu/group/xingbie",
          method: "get",
        }).then(({ data }) => {
          if (data && data.code === 0) {
            let res = data.data;
            let pArray = []
            for(let i=0;i<res.length;i++){
              pArray.push({
                value: parseFloat((res[i].total)),
                name: res[i].xingbie
              })
            }
            var option = {
              title: { text: '会员结构', left: 'center' },
              tooltip: { trigger: 'item', formatter: '{b} : {c} ({d}%)' },
              series: [{
                type: 'pie', radius: '55%', center: ['50%', '60%'], data: pArray,
                emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
              }]
            };
            chezhuChart1.setOption(option);
            window.onresize = function() { chezhuChart1.resize(); };
          }
        });
      })
    },

    getweixiujishiCount() {
      this.$http({ url: `weixiujishi/count`, method: "get" }).then(({ data }) => {
        if (data && data.code == 0) this.weixiujishiCount = data.data
      })
    },

    weixiujishiChat1() {
      this.$nextTick(()=>{
        var weixiujishiChart1 = echarts.init(document.getElementById("weixiujishiChart1"),'macarons');
        weixiujishiChart1.showLoading();

        this.$http({
          url: "pingjiafankui/getTechnicianPerformanceStats",
          method: "get",
        }).then(({ data }) => {
          weixiujishiChart1.hideLoading();
          if (data && data.code === 0 && data.stats.xAxis.length > 0) {
            var option = {
              title: { text: '美容师服务质量', left: 'center', show: false }, // 隐藏内部title让外部控制
              tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
              grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
              xAxis: {
                type: 'category',
                data: data.stats.xAxis,
                axisLabel: { interval: 0, rotate: 30 }
              },
              yAxis: { type: 'value', name: '平均得分', max: 5.0 },
              series: [{
                name: '综合评分',
                data: data.stats.seriesData,
                type: 'bar',
                barWidth: '40%',
                label: { show: true, position: 'top', formatter: '{c}分' },
                itemStyle: {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: '#d3a28c' },
                    { offset: 1, color: '#a84f64' }
                  ]),
                  borderRadius: [4, 4, 0, 0]
                }
              }]
            };
            weixiujishiChart1.setOption(option);
            window.addEventListener('resize', () => { weixiujishiChart1.resize(); });
          }
        });
      })
    },

    getweixiujiluCount() {
      this.$http({ url: `weixiujilu/count`, method: "get" }).then(({ data }) => {
        if (data && data.code == 0) this.weixiujiluCount = data.data
      })
    },

    /**
     * 核心优化：向后端传递日期参数，获取定制化报表数据
     */
    weixiujiluChat1() {
      this.$nextTick(()=>{
        var weixiujiluChart1 = echarts.init(document.getElementById("weixiujiluChart1"),'macarons');
        weixiujiluChart1.showLoading();

        // 构造传给后端的动态时间参数
        let params = {};
        if (this.dateRange && this.dateRange.length === 2) {
          params.startDate = this.dateRange[0];
          params.endDate = this.dateRange[1];
        }

        this.$http({
          url: `weixiujilu/selectRevenueStats`,
          method: "get",
          params: params  // 发送参数进行预处理过滤
        }).then(({ data }) => {
          weixiujiluChart1.hideLoading();
          if (data && data.code === 0 && data.stats && data.stats.xAxis.length > 0) {
            var option = {
              title: { show: false }, // 标题移到了外部div
              tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
              grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
              xAxis: { type: 'category', data: data.stats.xAxis, name: '月份' },
              yAxis: { type: 'value', name: '总营收 (元)' },
              series: [{
                name: '营业额',
                data: data.stats.seriesData,
                type: 'line',
                smooth: true,
                symbolSize: 8,
                itemStyle: { color: '#a84f64', borderWidth: 2 },
                areaStyle: {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: 'rgba(168, 79, 100, 0.45)' },
                    { offset: 1, color: 'rgba(168, 79, 100, 0.06)' }
                  ])
                }
              }]
            };
            weixiujiluChart1.setOption(option, true); // true表示强制覆盖旧数据刷新
            window.addEventListener('resize', () => { weixiujiluChart1.resize(); });
          } else {
            // 没有数据时清空图表并提示
            weixiujiluChart1.clear();
            this.$message.warning("该时间段内暂无营收数据");
          }
        });
      })
    },
  }
};
</script>

<style lang="scss" scoped>
.home-content {
  padding: 20px 30px;
	    color: var(--oc-text-muted);
  display: flex;
  font-size: 16px;
  justify-content: flex-start;
  flex-wrap: wrap;
  .home-title {
    border-radius: 5px;
    padding: 10px 0;
    box-shadow: 0 0px 0px rgba(0,0,0,.3);
    margin: 10px 0;
    display: none;
    width: 100%;
    justify-content: center;
    align-items: center;
    transition: 0.3s;
    order: -2;
    .titles {
      padding: 0 0 0 12px;
      color: #333;
      font-size: 24px;
      line-height: 44px;
    }
  }
  .user-box {
    border: 0px solid #ccc;
    padding: 10px 20px;
    margin: 10px;
    display: block;
    transition: 0.5s;
    border-radius: 20px;
    box-shadow: 0 0px 0px rgba(0,0,0,.3);
    flex-direction: column;
	    background: var(--oc-surface-2);
	    border: 1px solid var(--oc-border);
    flex: 1;
    width: calc(30% - 20px);
    justify-content: center;
    align-items: center;
    .user-top-box {
      padding: 20px 0 20px;
      margin: 0 0 10px;
      display: flex;
      border-color: #ccc;
      border-width:  0 0 1px;
      align-items: center;
      border-style: solid;
      .avatar {
        border-radius: 100%;
        object-fit: cover;
        width: 100px;
        min-width: 100px;
        height: 100px;
      }
      .user-top-item {
        width: 100%;
        .nickname {
          padding: 0;
          margin: 0 0 5px;
          line-height: 1.5;
          span { margin: 0 10px; color: #000; font-weight: 600; }
        }
        .role {
          padding: 0;
          line-height: 1.5;
          span { margin: 0 10px; color: #000; font-weight: 600; }
        }
      }
    }
    .user-bottom-box {
      flex-direction: column;
      display: flex;
      line-height: 2;
      align-items: flex-start;
      .ip, .time { display: flex; justify-content: center; }
	      .ip span:nth-child(1), .time span:nth-child(1) { color: var(--oc-text); }
	      .ip span:nth-child(2), .time span:nth-child(2) { color: var(--oc-text-muted); }
    }
  }
  .user-box:hover { border: 0; transform: translate3d(0, -5px, 0); }

  .statis-box {
    padding: 0;
    margin: 10px;
    display: flex;
    width: 100%;
    justify-content: space-around;
    align-items: center;
    order: -1;
    .statis1, .statis2, .statis3 {
      border-radius: 20px 100% 100%;
      margin: 0 10px 10px;
	      background: var(--oc-surface-2);
	      border: 1px solid var(--oc-border);
      display: flex;
      width: calc(20% - 20px);
      min-height: 280px;
      position: relative;
      transition: 0.5s;
      .left {
        border-radius: 12px 50px 50px;
	        background: linear-gradient(135deg, var(--oc-red), var(--oc-gold));
        display: flex;
        width: 68px;
        justify-content: center;
        align-items: center;
        height: 68px;
        .iconfont { color: #fff; font-size: 36px; }
      }
      .right {
        flex-direction: column;
        display: flex;
        width: 160px;
        justify-content: center;
        align-items: center;
	        .num { margin: 5px 0; color: var(--oc-gold); font-weight: bold; font-size: 48px; line-height: 48px; }
	        .name { margin: 5px 0; color: var(--oc-text-muted); font-size: 16px; line-height: 24px; }
      }
    }
    .statis1:hover, .statis2:hover, .statis3:hover {
      cursor: pointer;
      transform: translate3d(0, -5px, 0);
      z-index: 1;
    }
  }

  .type3 {
    padding: 0;
    align-content: flex-start;
    background: none;
    display: flex;
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
    height: auto;
    .echarts1, .echarts2 {
      border-radius: 20px;
      padding: 20px;
      margin: 10px;
	      background: var(--oc-surface-2);
	      border: 1px solid var(--oc-border);
      width: calc(50% - 20px);
      transition: 0.3s;
      height: 400px;
    }
    .echarts3 {
      border-radius: 20px;
      padding: 20px;
      margin: 10px;
	      background: var(--oc-surface-2);
	      border: 1px solid var(--oc-border);
      width: calc(100% - 20px);
      transition: 0.3s;
      height: 400px;
    }
    .echarts1:hover, .echarts2:hover, .echarts3:hover {
      transform: translate3d(0, -5px, 0);
      z-index: 1;
      box-shadow: 0 4px 15px rgba(0,0,0,0.1);
    }
  }
}
.animate__animated { animation-fill-mode: none; }
</style>
