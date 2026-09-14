import Vue from 'vue'
import VueRouter from 'vue-router'
Vue.use(VueRouter)

//引入组件
import Index from '../pages'
import Home from '../pages/home/home'
import Login from '../pages/login/login'
import Register from '../pages/register/register'
import Center from '../pages/center/center'
import Storeup from '../pages/storeup/list'
import payList from '../pages/pay'

import chezhuList from '../pages/chezhu/list'
import chezhuDetail from '../pages/chezhu/detail'
import chezhuAdd from '../pages/chezhu/add'
import weixiujishiList from '../pages/weixiujishi/list'
import weixiujishiDetail from '../pages/weixiujishi/detail'
import weixiujishiAdd from '../pages/weixiujishi/add'
import pinpaixinxiList from '../pages/pinpaixinxi/list'
import pinpaixinxiDetail from '../pages/pinpaixinxi/detail'
import pinpaixinxiAdd from '../pages/pinpaixinxi/add'
import xinnengyuanqicheList from '../pages/xinnengyuanqiche/list'
import xinnengyuanqicheDetail from '../pages/xinnengyuanqiche/detail'
import xinnengyuanqicheAdd from '../pages/xinnengyuanqiche/add'
import fuwufenleiList from '../pages/fuwufenlei/list'
import fuwufenleiDetail from '../pages/fuwufenlei/detail'
import fuwufenleiAdd from '../pages/fuwufenlei/add'
import shouhoufuwuList from '../pages/shouhoufuwu/list'
import shouhoufuwuDetail from '../pages/shouhoufuwu/detail'
import shouhoufuwuAdd from '../pages/shouhoufuwu/add'
import fuwuyuyueList from '../pages/fuwuyuyue/list'
import fuwuyuyueDetail from '../pages/fuwuyuyue/detail'
import fuwuyuyueAdd from '../pages/fuwuyuyue/add'
import weixiujiluList from '../pages/weixiujilu/list'
import weixiujiluDetail from '../pages/weixiujilu/detail'
import weixiujiluAdd from '../pages/weixiujilu/add'
import pingjiafankuiList from '../pages/pingjiafankui/list'
import pingjiafankuiDetail from '../pages/pingjiafankui/detail'
import pingjiafankuiAdd from '../pages/pingjiafankui/add'
import peijianxinxiList from '../pages/peijianxinxi/list'
import peijianxinxiDetail from '../pages/peijianxinxi/detail'
import peijianxinxiAdd from '../pages/peijianxinxi/add'
import peijianchukuList from '../pages/peijianchuku/list'
import peijianchukuDetail from '../pages/peijianchuku/detail'
import peijianchukuAdd from '../pages/peijianchuku/add'
import guzhangpaichaList from '../pages/guzhangpaicha/list'
import guzhangpaichaDetail from '../pages/guzhangpaicha/detail'
import guzhangpaichaAdd from '../pages/guzhangpaicha/add'
import guzhangfenleiList from '../pages/guzhangfenlei/list'
import guzhangfenleiDetail from '../pages/guzhangfenlei/detail'
import guzhangfenleiAdd from '../pages/guzhangfenlei/add'
import weixiuziliaoList from '../pages/weixiuziliao/list'
import weixiuziliaoDetail from '../pages/weixiuziliao/detail'
import weixiuziliaoAdd from '../pages/weixiuziliao/add'
import chatmessageList from '../pages/chatmessage/list'
import chatmessageDetail from '../pages/chatmessage/detail'
import chatmessageAdd from '../pages/chatmessage/add'
import friendList from '../pages/friend/list'
import friendDetail from '../pages/friend/detail'
import friendAdd from '../pages/friend/add'

const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
	return originalPush.call(this, location).catch(err => err)
}

//配置路由
export default new VueRouter({
	routes:[
		{
      path: '/',
      redirect: '/index/home'
    },
		{
			path: '/index',
			component: Index,
			children:[
				{
					path: 'home',
					component: Home
				},
				{
					path: 'center',
					component: Center,
				},
				{
					path: 'pay',
					component: payList,
				},
				{
					path: 'storeup',
					component: Storeup
				},
				{
					path: 'chezhu',
					component: chezhuList
				},
				{
					path: 'chezhuDetail',
					component: chezhuDetail
				},
				{
					path: 'chezhuAdd',
					component: chezhuAdd
				},
				{
					path: 'weixiujishi',
					component: weixiujishiList
				},
				{
					path: 'weixiujishiDetail',
					component: weixiujishiDetail
				},
				{
					path: 'weixiujishiAdd',
					component: weixiujishiAdd
				},
				{
					path: 'pinpaixinxi',
					component: pinpaixinxiList
				},
				{
					path: 'pinpaixinxiDetail',
					component: pinpaixinxiDetail
				},
				{
					path: 'pinpaixinxiAdd',
					component: pinpaixinxiAdd
				},
				{
					path: 'xinnengyuanqiche',
					component: xinnengyuanqicheList
				},
				{
					path: 'xinnengyuanqicheDetail',
					component: xinnengyuanqicheDetail
				},
				{
					path: 'xinnengyuanqicheAdd',
					component: xinnengyuanqicheAdd
				},
				{
					path: 'fuwufenlei',
					component: fuwufenleiList
				},
				{
					path: 'fuwufenleiDetail',
					component: fuwufenleiDetail
				},
				{
					path: 'fuwufenleiAdd',
					component: fuwufenleiAdd
				},
				{
					path: 'shouhoufuwu',
					component: shouhoufuwuList
				},
				{
					path: 'shouhoufuwuDetail',
					component: shouhoufuwuDetail
				},
				{
					path: 'shouhoufuwuAdd',
					component: shouhoufuwuAdd
				},
				{
					path: 'fuwuyuyue',
					component: fuwuyuyueList
				},
				{
					path: 'fuwuyuyueDetail',
					component: fuwuyuyueDetail
				},
				{
					path: 'fuwuyuyueAdd',
					component: fuwuyuyueAdd
				},
				{
					path: 'weixiujilu',
					component: weixiujiluList
				},
				{
					path: 'weixiujiluDetail',
					component: weixiujiluDetail
				},
				{
					path: 'weixiujiluAdd',
					component: weixiujiluAdd
				},
				{
					path: 'pingjiafankui',
					component: pingjiafankuiList
				},
				{
					path: 'pingjiafankuiDetail',
					component: pingjiafankuiDetail
				},
				{
					path: 'pingjiafankuiAdd',
					component: pingjiafankuiAdd
				},
				{
					path: 'peijianxinxi',
					component: peijianxinxiList
				},
				{
					path: 'peijianxinxiDetail',
					component: peijianxinxiDetail
				},
				{
					path: 'peijianxinxiAdd',
					component: peijianxinxiAdd
				},
				{
					path: 'peijianchuku',
					component: peijianchukuList
				},
				{
					path: 'peijianchukuDetail',
					component: peijianchukuDetail
				},
				{
					path: 'peijianchukuAdd',
					component: peijianchukuAdd
				},
				{
					path: 'guzhangpaicha',
					component: guzhangpaichaList
				},
				{
					path: 'guzhangpaichaDetail',
					component: guzhangpaichaDetail
				},
				{
					path: 'guzhangpaichaAdd',
					component: guzhangpaichaAdd
				},
				{
					path: 'guzhangfenlei',
					component: guzhangfenleiList
				},
				{
					path: 'guzhangfenleiDetail',
					component: guzhangfenleiDetail
				},
				{
					path: 'guzhangfenleiAdd',
					component: guzhangfenleiAdd
				},
				{
					path: 'weixiuziliao',
					component: weixiuziliaoList
				},
				{
					path: 'weixiuziliaoDetail',
					component: weixiuziliaoDetail
				},
				{
					path: 'weixiuziliaoAdd',
					component: weixiuziliaoAdd
				},
				{
					path: 'chatmessage',
					component: chatmessageList
				},
				{
					path: 'chatmessageDetail',
					component: chatmessageDetail
				},
				{
					path: 'chatmessageAdd',
					component: chatmessageAdd
				},
				{
					path: 'friend',
					component: friendList
				},
				{
					path: 'friendDetail',
					component: friendDetail
				},
				{
					path: 'friendAdd',
					component: friendAdd
				},
			]
		},
		{
			path: '/login',
			component: Login
		},
		{
			path: '/register',
			component: Register
		},
	]
})
