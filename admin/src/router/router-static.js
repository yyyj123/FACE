import Vue from 'vue';
//配置路由
import VueRouter from 'vue-router'
Vue.use(VueRouter);
//1.创建组件
import Index from '@/views/index'
import Home from '@/views/home'
import Login from '@/views/login'
import NotFound from '@/views/404'
import UpdatePassword from '@/views/update-password'
import pay from '@/views/pay'
import register from '@/views/register'
import center from '@/views/center'
import storage from '@/utils/storage'
	import guzhangfenlei from '@/views/modules/guzhangfenlei/list'
	import peijianchuku from '@/views/modules/peijianchuku/list'
	import pinpaixinxi from '@/views/modules/pinpaixinxi/list'
	import weixiujilu from '@/views/modules/weixiujilu/list'
	import xinnengyuanqiche from '@/views/modules/xinnengyuanqiche/list'
	import weixiujishi from '@/views/modules/weixiujishi/list'
	import peijianxinxi from '@/views/modules/peijianxinxi/list'
	import fuwufenlei from '@/views/modules/fuwufenlei/list'
	import guzhangpaicha from '@/views/modules/guzhangpaicha/list'
	import chat from '@/views/modules/chat/list'
	import shouhoufuwu from '@/views/modules/shouhoufuwu/list'
	import fuwuyuyue from '@/views/modules/fuwuyuyue/list'
	import pingjiafankui from '@/views/modules/pingjiafankui/list'
	import weixiuziliao from '@/views/modules/weixiuziliao/list'
	import config from '@/views/modules/config/list'
import chezhu from '@/views/modules/chezhu/list'

const keepPersonalCenterForStaff = (to, from, next) => {
	if (storage.get('role') === '管理员') {
		next('/')
		return
	}
	next()
}


//2.配置路由   注意：名字
export const routes = [{
	path: '/',
	name: '系统首页',
	component: Index,
	children: [{
		// 这里不设置值，是把main作为默认页面
		path: '/',
		name: '系统首页',
		component: Home,
		meta: {icon:'', title:'center', affix: true}
	}, {
			path: '/updatePassword',
			name: '修改密码',
			component: UpdatePassword,
			beforeEnter: keepPersonalCenterForStaff,
			meta: {icon:'', title:'updatePassword'}
	}, {
		path: '/pay',
		name: '支付',
		component: pay,
		meta: {icon:'', title:'pay'}
	}, {
			path: '/center',
			name: '个人信息',
			component: center,
			beforeEnter: keepPersonalCenterForStaff,
			meta: {icon:'', title:'center'}
	}
	,{
		path: '/guzhangfenlei',
		name: '肌肤问题分类',
		component: guzhangfenlei
	}
	,{
		path: '/peijianchuku',
		name: '库存出库',
		component: peijianchuku
	}
	,{
		path: '/pinpaixinxi',
		name: '产品品牌',
		component: pinpaixinxi
	}
	,{
		path: '/weixiujilu',
		name: '服务记录',
		component: weixiujilu
	}
	,{
		path: '/xinnengyuanqiche',
		name: '护理套餐',
		component: xinnengyuanqiche
	}
	,{
		path: '/weixiujishi',
		name: '美容师',
		component: weixiujishi
	}
	,{
		path: '/peijianxinxi',
		name: '产品与耗材',
		component: peijianxinxi
	}
	,{
		path: '/fuwufenlei',
		name: '项目分类',
		component: fuwufenlei
	}
	,{
		path: '/guzhangpaicha',
		name: '护理建议',
		component: guzhangpaicha
	}
	,{
		path: '/chat',
		name: '在线咨询',
		component: chat
	}
	,{
		path: '/shouhoufuwu',
		name: '美容项目',
		component: shouhoufuwu
	}
	,{
		path: '/fuwuyuyue',
		name: '项目预约',
		component: fuwuyuyue
	}
	,{
		path: '/pingjiafankui',
		name: '服务评价',
		component: pingjiafankui
	}
	,{
		path: '/weixiuziliao',
		name: '护理知识',
		component: weixiuziliao
	}
	,{
		path: '/config',
		name: '轮播图管理',
		component: config
	}
	,{
		path: '/chezhu',
		name: '会员',
		component: chezhu
	}
	]
	},
	{
		path: '/login',
		name: 'login',
		component: Login,
		meta: {icon:'', title:'login'}
	},
	{
		path: '/register',
		name: 'register',
		component: register,
		meta: {icon:'', title:'register'}
	},
	{
		path: '*',
		component: NotFound
	}
]
//3.实例化VueRouter  注意：名字
const router = new VueRouter({
	mode: 'hash',
	/*hash模式改为history*/
	routes // （缩写）相当于 routes: routes
})
const originalPush = VueRouter.prototype.push
//修改原型对象中的push方法
VueRouter.prototype.push = function push(location) {
	return originalPush.call(this, location).catch(err => err)
}
export default router;
