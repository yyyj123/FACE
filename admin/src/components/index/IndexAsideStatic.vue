<template>
	<aside class="menu-preview oc-admin-sidebar__inner line-sidebar-shell" aria-label="管理导航">
		<header class="line-sidebar-header">
			<div v-if="!isCollapse" class="line-sidebar-brand">
				<span class="line-sidebar-brand__mark" aria-hidden="true"></span>
				<span><strong>FACE</strong><small>管理后台</small></span>
			</div>
			<button class="line-sidebar-toggle" type="button" :aria-label="isCollapse ? '展开导航' : '收起导航'" @click="collapse">
				<i :class="isCollapse ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i>
			</button>
		</header>
		<el-scrollbar :wrap-class="isCollapse ? 'scrollbar-wrapper scrollbar-wrapper-close' : 'scrollbar-wrapper scrollbar-wrapper-open'">
			<el-menu ref="lineSidebar" :default-active="activeMenu" :unique-opened="true"
				class="el-menu-vertical-2 line-sidebar" :collapse-transition="false" :collapse="isCollapse"
				@mousemove.native="handlePointerMove" @mouseleave.native="handlePointerLeave">
				<el-menu-item class="home line-sidebar__item" index="/" @click.native="menuHandler('')">
					<span class="line-sidebar__marker" aria-hidden="true"></span>
					<span class="line-sidebar__index">01</span>
					<i class="icon iconfont icon-shouye-zhihui"></i>
					<span slot="title">系统首页</span>
				</el-menu-item>
					<el-submenu v-if="$storage.get('role') !== '管理员'" class="user line-sidebar__item" index="1" :popper-append-to-body="false">
					<template slot="title">
						<span class="line-sidebar__marker" aria-hidden="true"></span>
						<span class="line-sidebar__index">02</span>
						<i class="icon iconfont icon-kuaijiezhifu"></i>
						<span>个人中心</span>
					</template>
					<el-menu-item index="/updatePassword" @click="menuHandler('updatePassword')">修改密码</el-menu-item>
					<el-menu-item index="/center" @click="menuHandler('center')">个人信息</el-menu-item>
				</el-submenu>
				<template v-for="(menu,index) in menuList.backMenu" v-if="menu.child.length && menu.child[0].tableName !== 'hasBoard'">
					<el-submenu v-if="menu.child.length > 1 || !verticalIsMultiple" :key="'group-' + index"
						class="other line-sidebar__item" :index="index + 2 + ''" :popper-append-to-body="false">
						<template slot="title">
							<span class="line-sidebar__marker" aria-hidden="true"></span>
							<span class="line-sidebar__index">{{ lineIndex(index + 3) }}</span>
							<i class="el-icon-menu" :class="icons[index]"></i>
							<span>{{ menu.menu + (verticalFlag ? '管理' : '') }}</span>
						</template>
						<el-menu-item v-for="(child,sort) in menu.child" :key="sort" :index="'/' + child.tableName" @click="menuHandler(child.tableName)">{{ child.menu }}</el-menu-item>
					</el-submenu>
					<el-menu-item v-else :key="'item-' + index" class="other line-sidebar__item"
						:index="'/' + menu.child[0].tableName" @click="menuHandler(menu.child[0].tableName)">
						<span class="line-sidebar__marker" aria-hidden="true"></span>
						<span class="line-sidebar__index">{{ lineIndex(index + 3) }}</span>
						<i class="el-icon-menu" :class="icons[index]"></i>
						<span slot="title">{{ menu.child[0].menu + (verticalFlag ? '管理' : '') }}</span>
					</el-menu-item>
				</template>
			</el-menu>
		</el-scrollbar>
	</aside>
</template>

<script>
import menu from '@/utils/menu'
export default {
	data() {
		return {
			menuList: [],
			dynamicMenuRoutes: [],
			role: '',
			user: null,
			avatar:'',
			icons: [
				'el-icon-s-cooperation',
				'el-icon-s-order',
				'el-icon-s-platform',
				'el-icon-s-fold',
				'el-icon-s-unfold',
				'el-icon-s-operation',
				'el-icon-s-promotion',
				'el-icon-s-release',
				'el-icon-s-ticket',
				'el-icon-s-management',
				'el-icon-s-open',
				'el-icon-s-shop',
				'el-icon-s-marketing',
				'el-icon-s-flag',
				'el-icon-s-comment',
				'el-icon-s-finance',
				'el-icon-s-claim',
				'el-icon-s-custom',
				'el-icon-s-opportunity',
				'el-icon-s-data',
				'el-icon-s-check',
				'el-icon-s-grid',
				'el-icon-menu',
				'el-icon-chat-dot-square',
				'el-icon-message',
				'el-icon-postcard',
				'el-icon-position',
				'el-icon-microphone',
				'el-icon-close-notification',
				'el-icon-bangzhu',
				'el-icon-time',
				'el-icon-odometer',
				'el-icon-crop',
				'el-icon-aim',
				'el-icon-switch-button',
				'el-icon-full-screen',
				'el-icon-copy-document',
				'el-icon-mic',
				'el-icon-stopwatch',
			],
			menulistBorderBottom: {},
			verticalFlag: false,
			isCollapse: false,
			verticalStyle2: {"isCollapse":false,"close":{"contentBox":{"hover":{},"active":{"margin":"0 0 0 54px"},"default":{"minHeight":"100%","padding":"0","margin":"0 0 0 64px","position":"relative","display":"block"}},"box":{"hover":{},"active":{"width":"54px"},"default":{"boxShadow":"1px 0 6px  rgba(64, 158, 255, .3)","overflow":"hidden","top":"0","left":"0","background":"#304156","bottom":"0","width":"64px","fontSize":"0px","position":"fixed","transition":"width 0.3s","height":"100%","zIndex":"1001"}},"title":{"img":{"hover":{},"default":{"width":"44px","objectFit":"cover","borderRadius":"100%","height":"44px"},"flag":true,"url":"http://codegen.caihongy.cn/20201114/7856ba26477849ea828f481fa2773a95.jpg"},"box":{"hover":{},"default":{"width":"100%","padding":"20px","display":"none","height":"auto"}},"text":{"hover":{},"showType":true,"default":{"padding":"0 0 0 12px","lineHeight":"44px","fontSize":"12px","color":"rgba(64, 158, 255, 1)"},"text":"欢迎使用"}},"menu":{"two":{"title":{"hover":{"padding":"0 20px","backgroundColor":"red !important","lineHeight":"56px","color":"#fff","height":"56px"},"active":{"padding":"0 20px","backgroundColor":"blue !important","lineHeight":"56px","color":"#fff","height":"56px"},"default":{"padding":"0 20px","backgroundColor":"#fff","lineHeight":"56px","color":"#666","height":"56px"}},"box":{"hover":{},"default":{"border":"none"}}},"box":{"hover":{},"default":{"border":0,"padding":"0","listStyle":"none","margin":"0","position":"relative","background":"#FFF"}},"one":{"box1":{"hover":{"color":"#fff","background":"blue"},"active":{"color":"#fff","background":"blue"},"default":{"cursor":"pointer","padding":"0 20px","whiteSpace":"nowrap","position":"relative","color":"#333","background":"#fff"}},"icon":{"hover":{},"default":{"verticalAlign":"middle","margin":"0","color":"inherit","textAlign":"center","display":"inline-block","width":"24px","fontSize":"18px"},"flag":true},"box":{"hover":{},"default":{"padding":"0","listStyle":"none","margin":"0"}},"title":{"hover":{},"default":{"width":"0","verticalAlign":"middle","fontSize":"14px","color":"inherit","height":"0"}},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","display":"none","fontSize":"12px","position":"absolute","right":"20px"}}}},"btn":{"icon":{"hover":{},"default":{"margin":"0 2px","fontSize":"14px","color":"#fff","height":"40px"},"text":"icon-xihuan"},"hover":{"opacity":"0.8"},"default":{"border":"0","cursor":"pointer","padding":"0 9px","margin":"0 0 10px","outline":"none","color":"#fff","borderRadius":"0","background":"rgba(64, 158, 255, 1)","width":"auto","fontSize":"14px","height":"40px"},"text":"切换"},"user":{"two":{"title":{"hover":{"padding":"0 20px","backgroundColor":"red !important","lineHeight":"56px","color":"#fff","height":"56px"},"active":{"padding":"0 20px","backgroundColor":"blue !important","lineHeight":"56px","color":"#fff","height":"56px"},"default":{"padding":"0 20px","backgroundColor":"#fff","lineHeight":"56px","color":"#656","height":"56px"}},"box":{"hover":{},"default":{"border":"none"}}},"one":{"box1":{"hover":{"color":"#fff","background":"blue"},"active":{"color":"#fff","background":"blue"},"default":{"cursor":"pointer","padding":"0 20px","whiteSpace":"nowrap","position":"relative","color":"#323","background":"#fff"}},"icon":{"hover":{},"default":{"verticalAlign":"middle","margin":"0","color":"inherit","textAlign":"center","display":"inline-block","width":"24px","fontSize":"18px"},"flag":true,"text":"icon-kuaijiezhifu"},"box":{"hover":{},"default":{"padding":"0","listStyle":"none","margin":"0"}},"title":{"hover":{},"default":{"width":"0","verticalAlign":"middle","fontSize":"14px","color":"inherit","height":"0"}},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","display":"none","fontSize":"12px","position":"absolute","right":"20px"}}}},"userinfo":{"nickname":{"hover":{},"default":{"fontSize":"24px","lineHeight":"1.5","color":"#fff","textAlign":"center"}},"img":{"hover":{},"default":{"width":"100%","objectFit":"cover","borderRadius":"20px","display":"block","height":"170px"}},"box":{"hover":{},"default":{"width":"100%","padding":"20px","display":"none","height":"auto"}}},"home":{"two":{"title":{"hover":{"padding":"0 20px","backgroundColor":"red !important","lineHeight":"56px","color":"#fff","height":"56px"},"active":{"padding":"0 20px","backgroundColor":"blue !important","lineHeight":"56px","color":"#fff","height":"56px"},"default":{"padding":"0 20px","backgroundColor":"#fff","lineHeight":"56px","color":"#646","height":"56px"}},"box":{"hover":{},"default":{"border":"none"}}},"one":{"box1":{"hover":{"color":"#fff","background":"blue"},"active":{"color":"#fff","background":"blue"},"default":{"cursor":"pointer","padding":"0 20px","whiteSpace":"nowrap","position":"relative","color":"#313","background":"#fff"}},"icon":{"hover":{},"default":{"verticalAlign":"middle","margin":"0","color":"inherit","textAlign":"center","display":"inline-block","width":"24px","fontSize":"18px"},"flag":true,"text":"icon-shouye-zhihui"},"box":{"hover":{},"default":{"padding":"0","listStyle":"none","margin":"0"}},"title":{"hover":{},"default":{"width":"0","verticalAlign":"middle","fontSize":"14px","color":"inherit","height":"0"}},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","display":"none","fontSize":"12px","position":"absolute","right":"20px"}}}}},"open":{"contentBox":{"hover":{},"default":{"minHeight":"100%","padding":"20px 20px 0 220px","margin":"0","position":"relative","background":"#f6f6f6","display":"block"}},"box":{"hover":{},"default":{"boxShadow":"0px 0 0px  rgba(64, 158, 255, .3)","padding":"30px 0px 0 0","bottom":"20px","transition":"width 0.3s","overflow":"hidden","top":"20px","borderRadius":"0","left":"20px","background":"url(http://codegen.caihongy.cn/20240806/e8d338674411486ca3212d3bd97a8d87.png) no-repeat left bottom / 180px auto,url(http://codegen.caihongy.cn/20240806/2bf67037336a42748888e16ec1e210ce.png) no-repeat left top / 180px auto,url(http://codegen.caihongy.cn/20240806/33bffa9c07f344808299c6c36f94d04c.png) repeat-y right center,#333","width":"200px","fontSize":"15px","position":"fixed","height":"calc(100% - 40px)","zIndex":"1001"}},"title":{"img":{"hover":{},"default":{"width":"44px","objectFit":"cover","borderRadius":"100%","height":"44px"},"flag":false,"url":"http://codegen.caihongy.cn/20201114/7856ba26477849ea828f481fa2773a95.jpg"},"box":{"hover":{},"default":{"width":"100%","padding":"0 10px","margin":"10px 0 0","alignItems":"center","display":"none","height":"auto"}},"text":{"hover":{},"showType":true,"default":{"padding":"0 0 0 12px","lineHeight":"1.5","fontSize":"16px","color":"#fff"},"text":"欢迎使用"}},"menu":{"two":{"title":{"hover":{"padding":"0 30px","lineHeight":"34px","color":"#2990ff","background":"none","height":"34px"},"active":{"padding":"0 30px","lineHeight":"34px","color":"#2990ff","background":"none","height":"34px"},"default":{"padding":"0 30px","lineHeight":"34px","fontSize":"14px","color":"#fff","background":"none","height":"34px"}},"box":{"hover":{},"default":{"border":"none","width":"100%","padding":"10px 0","margin":"0 0 5px","background":"none"}}},"box":{"hover":{},"default":{"border":0,"padding":"0 0 0 20px","listStyle":"none","margin":"0","position":"relative","background":"none"}},"one":{"box1":{"hover":{"color":"#2990ff","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"active":{"color":"#2990ff","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"default":{"cursor":"pointer","padding":"0 0 0 10px","whiteSpace":"nowrap","color":"#fff","background":"none","lineHeight":"50px","fontSize":"inherit","position":"relative","height":"auto"}},"icon":{"hover":{},"default":{"width":"auto","verticalAlign":"middle","margin":"0 3px","fontSize":"22px","color":"#2990ff","textAlign":"center"},"flag":true},"box":{"hover":{},"default":{"width":"100%","padding":"0","listStyle":"none","margin":"0 auto 5px","fontSize":"inherit","height":"auto"}},"title":{"hover":{},"default":{"color":"inherit","verticalAlign":"middle","fontSize":"inherit"}},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","display":"none","fontSize":"12px","position":"absolute","right":"28px"}}}},"btn":{"icon":{"hover":{},"default":{"margin":"0 2px","fontSize":"40px","color":"#000","height":"40px"},"text":"icon-kaiguan4"},"hover":{"opacity":"0.8"},"default":{"border":"0","cursor":"pointer","padding":"0 9px","color":"#000","borderRadius":"4px","background":"none","display":"none","width":"auto","fontSize":"14px","height":"30px"},"text":""},"user":{"two":{"title":{"hover":{"padding":"0 30px","lineHeight":"34px","color":"#ff6524","background":"none","height":"34px"},"active":{"padding":"0 30px","lineHeight":"34px","color":"#ff6524","background":"none","height":"34px"},"default":{"padding":"0 30px","lineHeight":"34px","fontSize":"14px","color":"#fff","background":"none","height":"34px"}},"box":{"hover":{},"default":{"border":"none","padding":"10px 0","margin":"0 0 5px","background":"none"}}},"one":{"box1":{"hover":{"color":"#ff6524","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"active":{"color":"#ff6524","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"default":{"cursor":"pointer","padding":"0 0 0 10px","whiteSpace":"nowrap","color":"#fff","background":"none","lineHeight":"50px","fontSize":"inherit","position":"relative","height":"auto"}},"icon":{"hover":{},"default":{"width":"auto","verticalAlign":"middle","margin":"0 3px","fontSize":"22px","color":"#ff6524","textAlign":"center"},"flag":true,"text":"icon-touxiang04"},"box":{"hover":{},"default":{"padding":"0","listStyle":"none","margin":"0 auto 5px","fontSize":"inherit","height":"auto"}},"title":{"hover":{},"default":{"color":"inherit","verticalAlign":"middle","fontSize":"inherit"},"text":"个人中心"},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","display":"none","fontSize":"12px","position":"absolute","right":"8px"}}}},"userinfo":{"nickname":{"hover":{},"default":{"fontSize":"24px","lineHeight":"1.5","color":"#fff","textAlign":"center"}},"img":{"hover":{},"default":{"width":"100%","objectFit":"cover","borderRadius":"20px","display":"block","height":"170px"}},"box":{"hover":{},"default":{"width":"100%","padding":"20px","display":"none","height":"auto"}}},"home":{"two":{"title":{"hover":{"padding":"0 40px","lineHeight":"50px","color":"#fff","background":"red","height":"50px"},"active":{"padding":"0 40px","lineHeight":"50px","color":"#fff","background":"blue","height":"50px"},"default":{"padding":"0 40px","lineHeight":"50px","color":"#664","background":"#fff","height":"50px"}},"box":{"hover":{},"default":{"border":"none","display":"none"}}},"one":{"box1":{"hover":{"color":"#00ad45","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"active":{"color":"#00ad45","background":"url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%"},"default":{"cursor":"pointer","padding":"0 0 0 10px","whiteSpace":"nowrap","color":"#fff","background":"none","lineHeight":"50px","fontSize":"inherit","position":"relative","height":"auto"}},"icon":{"hover":{},"default":{"width":"auto","verticalAlign":"middle","margin":"0 3px","fontSize":"22px","color":"#00ad45","textAlign":"center"},"flag":true,"text":"icon-home1"},"box":{"hover":{},"default":{"padding":"0","listStyle":"none","margin":"0 auto 5px","fontSize":"inherit","height":"auto"}},"title":{"hover":{},"default":{"color":"inherit","verticalAlign":"middle","fontSize":"inherit"},"text":"系统首页"},"arrow":{"hover":{},"default":{"verticalAlign":"middle","margin":"-7px 0 0 0","top":"50%","color":"inherit","fontSize":"12px","position":"absolute","right":"20px"}}}}}},
				verticalIsMultiple: true,
				lineTargets: [],
				lineCurrent: [],
				lineEffectRaf: null,
				lineLastFrame: 0,
			}
	},
	computed: {
		activeMenu() {
			const route = this.$route
			const {
				meta,
				path
			} = route
			// if st path, the sidebar will highlight the path you sete
			if (meta.activeMenu) {
				return meta.activeMenu
			}
			return path
		}
	},
	watch:{
		avatar(){
			this.$forceUpdate()
		},
	},
	mounted() {
		const menus = menu.list()
		if(menus) {
			this.menuList = menus
		} else {
			let params = {
				page: 1,
				limit: 1,
				sort: 'id',
			}
			
			this.$http({
				url: "menu/list",
				method: "get",
				params: params
			}).then(({
				data
			}) => {
				if (data && data.code === 0) {
					this.menuList = JSON.parse(data.data.list[0].menujson);
					this.$storage.set("menus", this.menuList);
				}
			})
		}
		this.role = this.$storage.get('role')
		
		for(let i=0;i<this.menuList.length;i++) {
			if(this.menuList[i].roleName == this.role) {
				this.menuList = this.menuList[i];
				break;
			}
		}
		this.styleChange()
		
		let sessionTable = this.$storage.get("sessionTable")
		this.$http({
			url: sessionTable + '/session',
			method: "get"
		}).then(({
			data
		}) => {
			if (data && data.code === 0) {
				if(sessionTable == 'chezhu') {
					this.avatar = data.data.touxiang
				}
				if(sessionTable == 'weixiujishi') {
					this.avatar = data.data.touxiang
				}
				if(sessionTable=='users') {
					this.avatar = data.data.image
				}
				this.user = data.data;
			} else {
				let message = this.$message
				message.error(data.msg);
			}
		});
	},
		created(){
			this.icons.sort(()=>{
				return (0.5-Math.random())
			})
		},
		beforeDestroy() {
			if (this.lineEffectRaf) cancelAnimationFrame(this.lineEffectRaf)
		},
		methods: {
			lineIndex(value) {
				return String(value).padStart(2, '0')
			},
			lineItems() {
				const menu = this.$refs.lineSidebar && this.$refs.lineSidebar.$el
				return menu ? Array.from(menu.querySelectorAll(':scope > .line-sidebar__item')) : []
			},
			handlePointerMove(event) {
				if (this.isCollapse) return
				const items = this.lineItems()
				items.forEach((item, index) => {
					const rect = item.getBoundingClientRect()
					const distance = Math.abs(event.clientY - (rect.top + rect.height / 2))
					const proximity = Math.max(0, 1 - distance / 96)
					this.lineTargets[index] = proximity * proximity * (3 - 2 * proximity)
				})
				this.startLineEffect()
			},
			handlePointerLeave() {
				this.lineTargets = this.lineTargets.map(() => 0)
				this.startLineEffect()
			},
			startLineEffect() {
				if (this.lineEffectRaf) return
				this.lineLastFrame = performance.now()
				this.lineEffectRaf = requestAnimationFrame(this.runLineEffect)
			},
			runLineEffect(now) {
				const items = this.lineItems()
				const elapsed = Math.min((now - this.lineLastFrame) / 1000, 0.05)
				const amount = 1 - Math.exp(-elapsed / 0.1)
				let moving = false
				items.forEach((item, index) => {
					const active = item.classList.contains('is-active') ? 1 : 0
					const target = Math.max(this.lineTargets[index] || 0, active)
					const current = this.lineCurrent[index] || 0
					const next = current + (target - current) * amount
					const value = Math.abs(target - next) < 0.002 ? target : next
					this.lineCurrent[index] = value
					item.style.setProperty('--effect', value.toFixed(4))
					if (value !== target) moving = true
				})
				this.lineLastFrame = now
				this.lineEffectRaf = moving ? requestAnimationFrame(this.runLineEffect) : null
			},
			collapse() {
		  this.isCollapse = !this.isCollapse
		  this.$emit('oncollapsechange', this.isCollapse)
		},
		styleChange() {
			this.$nextTick(() => {
								document.querySelectorAll('.el-menu-vertical-demo .el-submenu .el-menu').forEach(el => {
				  el.removeAttribute('style')
				  const icon = {"border":"none","display":"none"}
				  Object.keys(icon).forEach((key) => {
					el.style[key] = icon[key]
				  })
				})
											})
		},
		menuHandler(name) {
			let router = this.$router
			name = '/'+name
			router.push(name)
		},
	}
}
</script>
<style lang="scss" scoped>
	.menu-preview {
		.el-scrollbar {
			height: 100%;
	
			& ::v-deep .scrollbar-wrapper {
				overflow-x: hidden;
			}
		
			// 竖向
			.el-menu-vertical-demo {
				.el-submenu:first-of-type ::v-deep .el-submenu__title .el-submenu__icon-arrow {
					display: none;
				}
			}
			
			.el-menu-vertical-demo>.el-menu-item {
				cursor: pointer;
				padding: 0 20px;
				color: #333;
				white-space: nowrap;
				background: #fff;
				position: relative;
			}
			
			.el-menu-vertical-demo>.el-menu-item:hover {
				color: #fff;
				background: blue;
			}
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-submenu__title {
				cursor: pointer;
				padding: 0 20px;
				color: #333;
				white-space: nowrap;
				background: #fff;
				position: relative;
			}
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-submenu__title:hover {
				color: #fff;
				background: blue;
			}
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-submenu__title .el-submenu__icon-arrow {
				margin: -7px 0 0 0;
				top: 50%;
				color: inherit;
				vertical-align: middle;
				font-size: 12px;
				position: absolute;
				right: 20px;
			}
			
			.el-menu-vertical-demo .el-submenu {
				padding: 0;
				margin: 0;
				list-style: none;
			}
			
			// .el-menu-vertical-demo .el-submenu ::v-deep .el-menu {
// 					// 		border: none;
// 					// 		display: none;
// 					// }
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-menu .el-menu-item {
				padding: 0 40px;
				color: #666;
				background: #fff;
				line-height: 50px;
				height: 50px;
			}
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-menu .el-menu-item:hover {
				padding: 0 40px;
				color: #fff;
				background: red;
				line-height: 50px;
				height: 50px;
			}
			
			.el-menu-vertical-demo .el-submenu ::v-deep .el-menu .el-menu-item.is-active {
				padding: 0 40px;
				color: #fff;
				background: blue;
				line-height: 50px;
				height: 50px;
			}
			// 竖向
		}
	}
	// 竖向 样式二-open
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.other {
		font-size: inherit;
		background: none;
	}
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.home {
		font-size: inherit;
		background: none;
	}
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.other>.el-tooltip {
		cursor: pointer;
		padding: 0 0 0 10px;
		color: #fff;
		white-space: nowrap;
		background: none;
		font-size: inherit;
		line-height: 50px;
		position: relative;
		height: auto;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.other>.el-tooltip:hover {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title {
		cursor: pointer !important;
		padding: 0 0 0 10px !important;
		color: #fff !important;
		white-space: nowrap !important;
		background: none !important;
		font-size: inherit !important;
		line-height: 50px !important;
		position: relative !important;
		height: auto !important;
	}
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.other.is-active>.el-tooltip {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title:hover {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other.is-active ::v-deep .el-submenu__title {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title .iconfont {
		margin: 0 3px;
		color: #2990ff;
		width: auto;
		vertical-align: middle;
		font-size: 22px;
		text-align: center;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 28px;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 ::v-deep .el-submenu.other .el-menu {
		border: none;
		padding: 10px 0;
		margin: 0 0 5px;
		background: none;
		width: 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other .el-menu .el-menu-item {
		padding: 0 30px !important;
		color: #fff !important;
		background: none !important;
		font-size: 14px !important;
		line-height: 34px !important;
		height: 34px !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other .el-menu .el-menu-item:hover {
		padding: 0 30px !important;
		color: #2990ff !important;
		background: none !important;
		line-height: 34px !important;
		height: 34px !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.other .el-menu .el-menu-item.is-active {
		padding: 0 30px !important;
		color: #2990ff !important;
		background: none !important;
		line-height: 34px !important;
		height: 34px !important;
	}

	// 竖向 样式二-close
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.other>.el-tooltip {
		cursor: pointer;
		padding: 0 20px;
		color: #333;
		white-space: nowrap;
		background: #fff;
		position: relative;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.other>.el-tooltip:hover {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.other.is-active>.el-tooltip {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title {
		cursor: pointer !important;
		padding: 0 20px !important;
		color: #333 !important;
		white-space: nowrap !important;
		background: #fff !important;
		position: relative !important;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title:hover {
		color: #fff !important;
		background: blue !important;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title .iconfont {
		margin: 0;
		color: inherit;
		display: inline-block;
		vertical-align: middle;
		width: 24px;
		font-size: 18px;
		text-align: center;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 20px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other .el-menu {
		border: none;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other .el-menu--vertical.other .el-menu-item {
		background-color: #fff;
		padding: 0 20px;
		color: #666;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other .el-menu--vertical.other .el-menu-item:hover {
		background-color: red !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.other .el-menu--vertical.other .el-menu-item.is-active {
		background-color: blue !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}
	
	// 竖向 样式二-open-首页
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.home>.el-tooltip {
		cursor: pointer;
		padding: 0 0 0 10px;
		color: #fff;
		white-space: nowrap;
		background: none;
		font-size: inherit;
		line-height: 50px;
		position: relative;
		height: auto;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.home>.el-tooltip:hover {
		color: #2990ff;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.home.is-active>.el-tooltip {
		color: #2990ff;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title {
		cursor: pointer !important;
		padding: 0 0 0 10px !important;
		color: #fff !important;
		white-space: nowrap !important;
		background: none !important;
		font-size: inherit !important;
		line-height: 50px !important;
		position: relative !important;
		height: auto !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title:hover {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title .iconfont {
		margin: 0 3px;
		color: #2990ff;
		width: auto;
		vertical-align: middle;
		font-size: 22px;
		text-align: center;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 28px;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home .el-menu {
		border: none;
		padding: 10px 0;
		margin: 0 0 5px;
		background: none;
		width: 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home .el-menu .el-menu-item {
		padding: 0 30px;
		color: #fff;
		background: none;
		font-size: 14px;
		line-height: 34px;
		height: 34px;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home .el-menu .el-menu-item:hover {
		padding: 0 30px;
		color: #2990ff;
		background: none;
		line-height: 34px;
		height: 34px;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.home .el-menu .el-menu-item.is-active {
		padding: 0 30px;
		color: #2990ff;
		background: none;
		line-height: 34px;
		height: 34px;
	}
	
	// 竖向 样式二-close-首页
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.home>.el-tooltip {
		cursor: pointer;
		padding: 0 20px;
		color: #333;
		white-space: nowrap;
		background: #fff;
		position: relative;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.home>.el-tooltip:hover {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.home.is-active>.el-tooltip {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title {
		cursor: pointer;
		padding: 0 20px;
		color: #333;
		white-space: nowrap;
		background: #fff;
		position: relative;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title:hover {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title .iconfont {
		margin: 0;
		color: inherit;
		display: inline-block;
		vertical-align: middle;
		width: 24px;
		font-size: 18px;
		text-align: center;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 20px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home .el-menu {
		border: none;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home .el-menu--vertical.home .el-menu-item {
		background-color: #fff;
		padding: 0 20px;
		color: #666;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home .el-menu--vertical.home .el-menu-item:hover {
		background-color: red !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.home .el-menu--vertical.home .el-menu-item.is-active {
		background-color: blue !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}
	
	// 竖向 样式二-open-个人中心
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.user>.el-tooltip {
		cursor: pointer;
		padding: 0 0 0 10px;
		color: #fff;
		white-space: nowrap;
		background: none;
		font-size: inherit;
		line-height: 50px;
		position: relative;
		height: auto;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.user>.el-tooltip:hover {
		color: #2990ff;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2>.el-menu-item.user.is-active>.el-tooltip {
		color: #2990ff;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title {
		cursor: pointer !important;
		padding: 0 0 0 10px !important;
		color: #fff !important;
		white-space: nowrap !important;
		background: none !important;
		font-size: inherit !important;
		line-height: 50px !important;
		position: relative !important;
		height: auto !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title:hover {
		color: #2990ff !important;
		background: url(http://codegen.caihongy.cn/20240806/3ef9d5a16c774ce5a5e8e733841adfc9.png) no-repeat left center / 100% 100% !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title .iconfont {
		margin: 0 3px;
		color: #2990ff;
		width: auto;
		vertical-align: middle;
		font-size: 22px;
		text-align: center;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 28px;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 ::v-deep .el-submenu.user .el-menu {
		border: none;
		padding: 10px 0;
		margin: 0 0 5px;
		background: none;
		width: 100%;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user .el-menu .el-menu-item {
		padding: 0 30px !important;
		color: #fff !important;
		background: none !important;
		font-size: 14px !important;
		line-height: 34px !important;
		height: 34px !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user .el-menu .el-menu-item:hover {
		padding: 0 30px !important;
		color: #2990ff !important;
		background: none !important;
		line-height: 34px !important;
		height: 34px !important;
	}
	
	.scrollbar-wrapper-open .el-menu-vertical-2 .el-submenu.user .el-menu .el-menu-item.is-active {
		padding: 0 30px !important;
		color: #2990ff !important;
		background: none !important;
		line-height: 34px !important;
		height: 34px !important;
	}
	
	// 竖向 样式二-close-个人中心
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.user>.el-tooltip {
		cursor: pointer;
		padding: 0 20px;
		color: #333;
		white-space: nowrap;
		background: #fff;
		position: relative;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.user>.el-tooltip:hover {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2>.el-menu-item.user.is-active>.el-tooltip {
		color: #fff;
		background: blue;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title {
		cursor: pointer !important;
		padding: 0 20px !important;
		color: #333 !important;
		white-space: nowrap !important;
		background: #fff !important;
		position: relative !important;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title:hover {
		color: #fff !important;
		background: blue !important;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title .iconfont {
		margin: 0;
		color: inherit;
		display: inline-block;
		vertical-align: middle;
		width: 24px;
		font-size: 18px;
		text-align: center;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user ::v-deep .el-submenu__title .el-submenu__icon-arrow {
		margin: -7px 0 0 0;
		top: 50%;
		color: inherit;
		display: none;
		vertical-align: middle;
		font-size: 12px;
		position: absolute;
		right: 20px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user .el-menu {
		border: none;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user .el-menu--vertical.user .el-menu-item {
		background-color: #fff;
		padding: 0 20px;
		color: #666;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user .el-menu--vertical.user .el-menu-item:hover {
		background-color: red !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}
	
	.scrollbar-wrapper-close .el-menu-vertical-2 .el-submenu.user .el-menu--vertical.user .el-menu-item.is-active {
		background-color: blue !important;
		padding: 0 20px;
		color: #fff;
		line-height: 56px;
		height: 56px;
	}

	/* LineSidebar integration */
	.line-sidebar-shell {
		--line-accent: #c86f8a;
		--line-text: #b8aeb6;
		--line-marker: #5f555e;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		background: #151016 !important;
		border: 1px solid rgba(200, 111, 138, .2) !important;
		border-radius: 12px !important;
	}
	.line-sidebar-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 62px;
		padding: 10px 10px 10px 18px;
		border-bottom: 1px solid rgba(200, 111, 138, .14);
	}
	.line-sidebar-brand { display: flex; align-items: center; gap: 10px; color: var(--oc-text); }
	.line-sidebar-brand__mark { width: 3px; height: 30px; background: var(--line-accent); border-radius: 2px; }
	.line-sidebar-brand strong { display: block; font-size: 14px; line-height: 18px; letter-spacing: .04em; }
	.line-sidebar-brand small { display: block; color: var(--line-text); font-size: 11px; line-height: 16px; }
	.line-sidebar-toggle {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		color: var(--line-text);
		background: transparent;
		border: 0;
		border-radius: 8px;
		cursor: pointer;
		transition: color 180ms ease, background-color 180ms ease, transform 180ms cubic-bezier(.22,1,.36,1);
	}
	.line-sidebar-toggle:hover { color: var(--oc-text); background: rgba(200, 111, 138, .1); }
	.line-sidebar-toggle:active { transform: scale(.96); }
	.line-sidebar-toggle:focus-visible { outline: 2px solid var(--oc-focus); outline-offset: 2px; }
	.line-sidebar-shell > .el-scrollbar { flex: 1; min-height: 0; }
	.line-sidebar-shell ::v-deep .el-scrollbar__wrap { overflow-x: hidden; }
	.line-sidebar-shell ::v-deep .el-scrollbar__bar.is-horizontal { display: none !important; }
	.line-sidebar {
		box-sizing: border-box;
		width: 100%;
		padding: 14px 10px 24px 52px !important;
		background: transparent !important;
		border: 0 !important;
	}
	.line-sidebar ::v-deep > .line-sidebar__item {
		--effect: 0;
		box-sizing: border-box;
		position: relative;
		min-height: 46px !important;
		margin: 3px 0 !important;
		padding: 0 8px 0 0 !important;
		color: color-mix(in srgb, var(--line-accent) calc(var(--effect) * 100%), var(--line-text)) !important;
		background: transparent !important;
		box-shadow: none !important;
		transform: translateX(calc(var(--effect) * 14px));
		transition: none;
	}
	.line-sidebar ::v-deep > .line-sidebar__item::after {
		content: '';
		position: absolute;
		top: calc(100% + 3px);
		left: -43px;
		width: 18px;
		height: 1px;
		background: var(--line-marker);
		opacity: .45;
		transform-origin: left center;
		transform: scaleX(calc(.7 + var(--effect) * .55));
	}
	.line-sidebar ::v-deep > .line-sidebar__item:last-child::after { display: none; }
	.line-sidebar ::v-deep .line-sidebar__marker {
		position: absolute;
		top: 23px;
		left: -43px;
		width: 36px;
		height: 1px;
		background: color-mix(in srgb, var(--line-accent) calc(var(--effect) * 100%), var(--line-marker));
		transform-origin: left center;
		transform: scaleX(calc(.72 + var(--effect) * .42));
	}
	.line-sidebar ::v-deep .line-sidebar__index {
		display: inline-block;
		width: 24px;
		margin-right: 4px;
		font: 500 10px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
		opacity: calc(.45 + var(--effect) * .55);
	}
	.line-sidebar ::v-deep > .el-menu-item,
	.line-sidebar ::v-deep > .el-submenu > .el-submenu__title {
		height: 46px !important;
		line-height: 46px !important;
		font-size: 14px !important;
		background: transparent !important;
		color: inherit !important;
	}
	.line-sidebar ::v-deep > .el-submenu > .el-submenu__title { padding: 0 8px 0 0 !important; }
	.line-sidebar ::v-deep > .line-sidebar__item i:not(.el-submenu__icon-arrow) {
		width: 22px;
		margin: 0 8px 0 0 !important;
		color: inherit !important;
		font-size: 17px !important;
		text-align: center;
	}
	.line-sidebar ::v-deep .el-submenu__icon-arrow { right: 4px; color: inherit; font-size: 11px; }
	.line-sidebar ::v-deep .el-submenu .el-menu {
		margin: 0 0 6px !important;
		padding: 2px 0 5px !important;
		background: transparent !important;
	}
	.line-sidebar ::v-deep .el-submenu .el-menu-item {
		box-sizing: border-box;
		height: 36px !important;
		min-height: 36px !important;
		padding: 0 10px 0 55px !important;
		color: #938892 !important;
		background: transparent !important;
		font-size: 13px !important;
		line-height: 36px !important;
	}
	.line-sidebar ::v-deep .el-submenu .el-menu-item:hover,
	.line-sidebar ::v-deep .el-submenu .el-menu-item.is-active { color: #eadfe5 !important; background: rgba(200, 111, 138, .08) !important; }
	.line-sidebar ::v-deep > .line-sidebar__item.is-active { --effect: 1 !important; }
	.line-sidebar ::v-deep > .line-sidebar__item.is-active,
	.line-sidebar ::v-deep > .el-submenu.is-active > .el-submenu__title { color: #e39ab0 !important; }
	.scrollbar-wrapper-close .line-sidebar { padding: 12px 8px 20px !important; }
	.scrollbar-wrapper-close .line-sidebar ::v-deep .line-sidebar__marker,
	.scrollbar-wrapper-close .line-sidebar ::v-deep .line-sidebar__index,
	.scrollbar-wrapper-close .line-sidebar ::v-deep > .line-sidebar__item::after { display: none; }
	.scrollbar-wrapper-close .line-sidebar ::v-deep > .line-sidebar__item { transform: none; padding: 0 !important; }
	@media (prefers-reduced-motion: reduce) {
		.line-sidebar ::v-deep > .line-sidebar__item { transform: none !important; }
		.line-sidebar-toggle { transition: none; }
	}
</style>
