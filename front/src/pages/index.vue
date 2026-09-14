<template>
	<div class="main-containers oc-shell" @keydown.esc.stop="closeShellMenus">
		<div class="body-containers">
			<header class="oc-header">
				<div class="oc-header__inner">
					<button class="oc-brand" type="button" @click="goMenu('/index/home')" aria-label="返回网站首页">
						<span class="oc-brand__mark" aria-hidden="true"></span>
							<span><strong>FACE 护理</strong><small>专业美容与到店预约</small></span>
					</button>
					<button class="oc-menu-toggle" type="button" :aria-expanded="String(mobileMenuOpen)" aria-controls="oc-primary-navigation" @click="mobileMenuOpen = !mobileMenuOpen">
						<span aria-hidden="true"></span><span aria-hidden="true"></span><span class="sr-only">切换主导航</span>
					</button>
					<nav id="oc-primary-navigation" class="oc-nav" :class="{'is-open': mobileMenuOpen}" aria-label="主导航">
						<button type="button" class="oc-nav__item" :class="{'is-active': activeMenu=='/index/home'}" @click="goMenu('/index/home')">首页</button>
						<div class="oc-nav__group" v-for="(item,index) in menuList" :key="index" @mouseenter="item.hasCate && menuShowClick4(index)" @mouseleave="handleGroupMouseLeave($event,index)" @focusout="handleGroupFocusOut($event,index)">
							<div class="oc-nav__category">
								<button type="button" class="oc-nav__item" :class="{'is-active': activeMenu==item.url}" @click.stop="goMenu(item.url)">{{item.name}}</button>
								<button v-if="item.hasCate" type="button" class="oc-nav__submenu-toggle" aria-haspopup="menu" :aria-expanded="String(showType4==index)" :aria-controls="`oc-submenu-${index}`" :aria-label="`${item.name}分类菜单`" @click.stop="toggleSubmenu(index)"><span aria-hidden="true">⌄</span></button>
							</div>
							<div v-if="showType4==index&&item.hasCate" :id="`oc-submenu-${index}`" class="oc-nav__submenu" role="menu">
								<button type="button" role="menuitem" v-for="(items,indexs) in item.cateList" :key="indexs" @click.stop="cateClick(item.url,items)">{{items}}</button>
							</div>
						</div>
						<button type="button" class="oc-nav__item" @click="goChat" v-if="Token">在线客服</button>
					</nav>
					<el-dropdown class="oc-account" @command="handleCommand" trigger="click">
						<button type="button" class="oc-account__trigger" v-if="Token">
							<img v-if="headportrait&&Token" :src="headportrait?baseUrl + headportrait:require('@/assets/avator.png')" alt="">
							<span>{{username || '账户'}}</span><span class="icon iconfont icon-xiala" aria-hidden="true"></span>
						</button>
						<button type="button" class="oc-account__trigger" v-else @click.stop="toLogin">登录</button>
						<el-dropdown-menu class="top-el-dropdown-menu" slot="dropdown" v-if="Token">
							<el-dropdown-item class="service-item" :command="'service'">在线咨询</el-dropdown-item>
							<el-dropdown-item v-if="notAdmin" class="user-item" :command="'user'">个人中心</el-dropdown-item>
							<el-dropdown-item class="register-item" :command="'register'">退出</el-dropdown-item>
						</el-dropdown-menu>
					</el-dropdown>
				</div>
			</header>

			<main class="oc-main">
				<section class="oc-hero" v-if="isHomePage" aria-labelledby="oc-hero-title">
					<div class="oc-hero__copy">
							<p class="oc-hero__status"><span aria-hidden="true"></span> 项目、时间与服务记录，随时清晰可查</p>
								<h1 id="oc-hero-title">{{ homepageHeroTitle }}</h1>
							<p>从项目选择到到店服务，用清晰的预约流程连接美容师、时间与持续护理。</p>
							<el-button type="primary" @click="goMenu('/index/shouhoufuwu')">预约美容项目</el-button>
					</div>
						<div class="oc-hero__media">
							<div class="oc-hero__slides">
								<div class="oc-hero__slide" v-for="(item,index) in heroSlides" :key="item.id || index" v-show="index === carouselActiveIndex">
									<button type="button" class="oc-hero__image" @click="carouselClick(item.url)" :aria-label="item.url ? '打开轮播内容' : '查看美容护理服务'">
										<picture><source v-if="item.mobileImage" media="(max-width: 600px)" :srcset="item.mobileImage"><img :src="item.image" :alt="item.alt" @error="carouselImageError($event,item.fallbackImage)"></picture>
									</button>
								</div>
							</div>
							<div class="swiper-pagination" aria-label="轮播图分页">
								<button v-for="(item,index) in heroSlides" :key="`dot-${item.id || index}`" type="button" class="swiper-pagination-bullet" :class="{'swiper-pagination-bullet-active': index === carouselActiveIndex}" :aria-label="`查看第 ${index + 1} 张轮播图`" @click="selectCarouselSlide(index)"></button>
							</div>
							<button class="swiper-button-prev" type="button" aria-label="上一张" @click="previousCarouselSlide"><span class="icon iconfont icon-jiantou39" aria-hidden="true"></span></button>
							<button class="swiper-button-next" type="button" aria-label="下一张" @click="nextCarouselSlide"><span class="icon iconfont icon-jiantou18" aria-hidden="true"></span></button>
						</div>
				</section>

				<section class="oc-service-paths" v-if="isHomePage" aria-labelledby="oc-paths-title">
						<div class="oc-service-paths__intro"><h2 id="oc-paths-title">从选择到到店，一次完成</h2><p>常用入口按顾客任务组织，让项目、预约与护理记录更容易找到。</p></div>
						<button class="oc-path oc-path--vehicle" type="button" @click="goMenu('/index/xinnengyuanqiche')"><span class="oc-path__media oc-path__media--package" aria-hidden="true">套餐</span><span class="oc-path__copy"><strong>护理套餐</strong><small>查看组合项目、次数与适用说明</small></span></button>
						<button class="oc-path oc-path--service" type="button" @click="goMenu('/index/shouhoufuwu')"><span class="oc-path__media oc-path__media--service" aria-hidden="true">项目</span><span class="oc-path__copy"><strong>预约项目</strong><small>了解时长与价格，快速发起预约</small></span></button>
						<button class="oc-path oc-path--diagnosis" type="button" @click="goMenu('/index/guzhangpaicha')"><span class="oc-path__media oc-path__media--care" aria-hidden="true">护理</span><span class="oc-path__copy"><strong>护理建议</strong><small>按肌肤关注点查看护理方向</small></span></button>
						<button class="oc-path oc-path--records" type="button" @click="Token && notAdmin ? goMenu('/index/center') : toLogin()"><span class="oc-path__index">个人档案</span><strong>跟踪预约与护理记录</strong><small>{{Token ? '进入个人中心查看预约和服务进度' : '登录后查看您的到店服务档案'}}</small><span class="oc-path__link">{{Token ? '查看记录' : '立即登录'}} →</span></button>
				</section>

					<div class="oc-route-container"><router-view id="scrollView" :key="$route.fullPath"></router-view></div>
			</main>

			<footer class="oc-footer">
					<div><strong>FACE 护理</strong><p>美容项目预约与会员护理服务平台</p></div>
					<nav aria-label="页脚导航"><button type="button" @click="goMenu('/index/home')">首页</button><button type="button" @click="goMenu('/index/shouhoufuwu')">美容项目</button><button type="button" @click="goMenu('/index/guzhangpaicha')">护理建议</button></nav>
					<div class="oc-footer__legal" v-html="bottomContent || '专业 · 清晰 · 持续护理'"></div>
			</footer>
		</div>
		
			<el-dialog title="在线咨询" custom-class="oc-chat-dialog" append-to-body :visible.sync="chatFormVisible" width="600px" :close-on-click-modal="false" :before-close="chatClose">
				<div class="chat-content" id="chat-content">
					<div v-if="!chatList.length" class="oc-chat-empty">
						<strong>欢迎联系 FACE 在线客服</strong>
						<span>请描述您想了解的项目、预约时间或护理问题，我们会尽快回复。</span>
					</div>
				<div v-bind:key="item.id" v-for="item in chatList">
					<div v-if="item.addtime" style="width: 100%;text-align: center;font-size: 10px;color: #666;">{{timeFormat(item.addtime)}}</div>
					<div v-if="item.ask" class="right-content">
						<div style="display: flex;align-items: flex-start;">
							<el-alert v-if="item.type==1" class="text-content" :title="item.ask" :closable="false"
								type="warning"></el-alert>
							<el-image v-else-if="item.type==2" :src="baseUrl + item.ask" style="width: 150px;height: 150px;" fit="cover" :preview-src-list="[baseUrl + item.ask]"></el-image>
							<video v-else-if="item.type==3" :src="baseUrl + item.ask" style="width: 280px;" controls></video>
							<el-button v-else-if="item.type==4" type="primary" size="mini" @click="download(item.ask)">文件预览</el-button>
								<img loading="lazy" :src="item.uimage?(baseUrl + item.uimage):require('@/assets/avator.png')" style="width: 30px;height: 30px;border-radius: 50%;margin: 0 0 0 5px;" alt="咨询用户头像">
						</div>
					</div>
					<div v-else class="left-content">
						<div style="display: flex;align-items: flex-start;">
								<img loading="lazy" :src="item.uimage?(baseUrl + item.uimage.split(',')[0]):require('@/assets/avator.png')" style="width: 30px;height: 30px;border-radius: 50%;margin: 0 5px 0 0;" alt="客服头像">
							<el-alert v-if="item.type==1" class="text-content" :title="item.reply" :closable="false"
								type="success"></el-alert>
							<el-image v-else-if="item.type==2" :src="baseUrl + item.reply" style="width: 150px;height: 150px;" fit="cover" :preview-src-list="[baseUrl + item.reply]"></el-image>
							<video v-else-if="item.type==3" :src="baseUrl + item.reply" style="width: 280px;" controls></video>
							<el-button v-else-if="item.type==4" type="primary" size="mini" @click="download(item.reply)">文件预览</el-button>
						</div>
					</div>
					<div class="clear-float"></div>
				</div>
			</div>
			<div slot="footer" class="dialog-footer">
				<div v-if="askShow"
					style="padding-bottom: 10px;display: flex;align-items: center;justify-content: center;">
					<el-upload class="upload-demo" :action="uploadUrl" :on-success="uploadSuccess" accept=".jpg,.png"
						:show-file-list="false">
						<el-button size="mini" type="success">上传图片</el-button>
					</el-upload>
					<el-upload class="upload-demo" :action="uploadUrl" :on-success="uploadSuccess2" accept=".mp4"
						:show-file-list="false">
						<el-button size="mini" type="success" style="margin: 0 0 0 10px;">上传视频</el-button>
					</el-upload>
					<el-upload class="upload-demo" :action="uploadUrl" :on-success="uploadSuccess3"
						:show-file-list="false">
						<el-button size="mini" type="success" style="margin: 0 0 0 10px;">上传文件</el-button>
					</el-upload>
				</div>
				<div class="oc-chat-composer">
					<button type="button" class="oc-chat-tool oc-chat-attachment" :aria-expanded="String(askShow)" aria-label="打开附件选项" @click="askShow = !askShow"><img src="../assets/jiahao.png" alt=""></button>
					<el-input @keydown.enter.native.exact.prevent="addChat(null)" v-model="form.ask" placeholder="请输入咨询内容" aria-label="咨询内容">
					</el-input>
					<button class="oc-chat-send" type="button" :disabled="chatSending || !form.ask.trim()" @click="addChat(null)">{{chatSending ? '发送中…' : '发送'}}</button>
					<div style="position: relative;">
						<button type="button" class="oc-chat-tool oc-chat-emoji" :aria-expanded="String(showEmoji)" aria-controls="oc-emoji-picker" aria-label="选择表情" @click="showEmoji=!showEmoji"><span class="icon iconfont icon-gerenzhongxin-zhihui" aria-hidden="true"></span></button>
						<picker
							id="oc-emoji-picker"
							:include="['people', 'Smileys']"
							:showSearch="false"
							:showPreview="false"
							:showCategories="false"
							@select="addEmoji"
							v-if="showEmoji"
							:backgroundImageFn="((set,sheetSize)=>{
								return require('@/assets/32.png')
							})"
							style="position: absolute;bottom: 40px;left: -100px;"
						/>
					</div>
				</div>
			</div>
		</el-dialog>
	</div>
</template>

<script>
	import Vue from 'vue'
		import axios from 'axios'
	import { Picker } from "emoji-mart-vue";
	import timeMethod from '@/common/timeMethod'
	import {
		WebsocketMixin
	} from '@/mixins/WebsocketMixin'
export default {
	components:{
		Picker
	},
	mixins: [WebsocketMixin],
	data() {
		return {
				mobileMenuOpen: false,
				swiperInitTimer: null,
				carouselAdvanceTimer: null,
				carouselActiveIndex: 0,
					motionQuery: null,
				reducedMotion: false,
				activeIndex: '0',
				baseUrl: '',
				homepageHeroTitle: '为每一次护理预留从容',
				carouselList: [],
				menuList: [],
			chatFormVisible: false,
				chatList: [],
				chatSending: false,
			headers: {
				Token: localStorage.getItem('frontToken')
			},
			uploadUrl: this.$config.baseUrl + 'file/upload',
			askShow: false,
			showEmoji: false,
			form: {
				ask: '',
			},
			headportrait: localStorage.getItem('frontHeadportrait')?localStorage.getItem('frontHeadportrait'):'',
			Token: localStorage.getItem('frontToken'),
			username: localStorage.getItem('username'),
			notAdmin: localStorage.getItem('frontSessionTable')!='"users"',
			iconArr: [
				'el-icon-star-off',
				'el-icon-goods',
				'el-icon-warning',
				'el-icon-question',
				'el-icon-info',
				'el-icon-help',
				'el-icon-picture-outline-round',
				'el-icon-camera-solid',
				'el-icon-video-camera-solid',
				'el-icon-video-camera',
				'el-icon-bell',
				'el-icon-s-cooperation',
				'el-icon-s-order',
				'el-icon-s-platform',
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
				'el-icon-s-opportunity',
				'el-icon-s-data',
				'el-icon-s-check'
			],
			bottomContent: '',
			showType4: -1,
		}
	},
	async created() {
			this.baseUrl = this.$config.baseUrl;
			this.menuList = this.$config.indexNav;
			this.getHomepageTitle();
			this.getCarousel();
		if(localStorage.getItem('frontToken') && localStorage.getItem('frontToken')!=null) {
			this.getSession()
		}
		this.cateList = this.$config.cateList
		if(this.cateList.length){
			try {
				const rs = await this.$http.get('api/v1/service-categories', { params: { shopId: 1 } })
				const categories = rs.data && rs.data.code === 0 ? rs.data.data : []
				this.menuList.forEach(menu => {
					if (this.cateList.some(item => item.name === menu.name)) {
						menu.cateList = categories.map(item => item.name)
						menu.hasCate = categories.length > 0
					}
				})
			} catch (error) {
				this.menuList.forEach(menu => { menu.hasCate = false })
			}
		}
	},
		mounted() {
			const currentUrl = new URL(window.location.href)
			if (currentUrl.searchParams.has('__face_home')) {
				currentUrl.searchParams.delete('__face_home')
				window.history.replaceState(null, '', currentUrl.pathname + currentUrl.search + currentUrl.hash)
			}
			this.activeIndex = localStorage.getItem('keyPath') || '0';
		document.addEventListener('pointerdown', this.handleOutsideClick)
		this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
		this.handleMotionPreference(this.motionQuery)
		if (this.motionQuery.addEventListener) this.motionQuery.addEventListener('change', this.handleMotionPreference)
		else this.motionQuery.addListener(this.handleMotionPreference)
			this.syncHeroCarousel()
	},
	beforeDestroy() {
		document.removeEventListener('pointerdown', this.handleOutsideClick)
		if (this.motionQuery) {
			if (this.motionQuery.removeEventListener) this.motionQuery.removeEventListener('change', this.handleMotionPreference)
			else this.motionQuery.removeListener(this.handleMotionPreference)
			}
			clearTimeout(this.swiperInitTimer)
			clearInterval(this.carouselAdvanceTimer)
	},
			computed: {
				isHomePage() {
					return this.$route.path === '/index/home'
				},
					heroSlides() {
					if (this.carouselList.length) {
						return this.carouselList.map((item, index) => ({
							id: item.id,
							url: item.targetType === 'NONE' ? '' : (item.targetValue || ''),
								image: this.carouselImage(item.imageUrl, index),
							fallbackImage: this.carouselFallback(index, false),
							mobileImage: this.carouselImage(item.imageUrl),
							alt: item.title || `FACE 美容护理轮播图 ${index + 1}`
						}))
					}
					const routes = ['/index/shouhoufuwu', '/index/xinnengyuanqiche', '/index/guzhangpaicha']
					return routes.map((url, index) => ({
						id: `salon-hero-${index + 1}`,
						url,
						image: this.carouselFallback(index, false),
					fallbackImage: this.carouselFallback(index, false),
					mobileImage: this.carouselFallback(index, true),
					alt: this.carouselAlt(index)
				}))
			},
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
		},
	},
	watch: {
				carouselList() {
					this.carouselActiveIndex = 0
					this.$nextTick(() => this.syncHeroCarousel())
			},
			$route(newValue) {
				this.closeShellMenus()
				this.$nextTick(() => this.syncHeroCarousel())
				for (let x in this.menuList) {
					if (newValue.path == this.menuList[x].url) {
						this.activeIndex = x
					}
				}
				this.Token = localStorage.getItem('frontToken')
				this.$forceUpdate()
				this.$nextTick(() => this.scrollToRoute(newValue.path))
		},
		headportrait(){
			this.$forceUpdate()
		},
			},
					methods: {
					getHomepageTitle() {
						this.$http.get('config/homepage-title').then(res => {
							if (res.data && res.data.code === 0 && res.data.data && res.data.data.title) {
								this.homepageHeroTitle = res.data.data.title
							}
						}).catch(() => {})
					},
					carouselFallback(index, mobile) {
				const slot = index % 3
				const names = ['facial', 'hydration', 'body']
				return mobile
					? require(`@/assets/images/salon/hero-${names[slot]}-mobile.webp`)
					: require(`@/assets/images/salon/hero-${names[slot]}.webp`)
			},
			carouselAlt(index) {
				return ['静谧空间中的专业面部护理', '水光精华与山茶花艺术静物', '暮色美容空间中的肩颈护理'][index % 3]
		},
			carouselImage(value, index = 0) {
				const fallback = this.carouselFallback(index, false)
				if (!value || typeof window === 'undefined') return fallback
				if (/^(https?:|data:|blob:)/i.test(value)) return value
				const root = this.baseUrl.endsWith('/') ? this.baseUrl : `${this.baseUrl}/`
				return `${root}${String(value).replace(/^\.?\//, '')}`
			},
		carouselImageError(event, fallback) {
			if (event.currentTarget.src !== fallback) event.currentTarget.src = fallback
		},
				syncHeroCarousel() {
					clearTimeout(this.swiperInitTimer)
					clearInterval(this.carouselAdvanceTimer)
					if (!this.$el) return
					if (!this.isHomePage) return
					this.swiperInitTimer = setTimeout(() => this.startCarouselTimer(), 0)
				},
				startCarouselTimer() {
					clearInterval(this.carouselAdvanceTimer)
					if (this.heroSlides.length < 2) return
					this.carouselAdvanceTimer = window.setInterval(() => {
						if (this.isHomePage) this.advanceCarouselIndex(1, false)
					}, 5000)
				},
				advanceCarouselIndex(step, restartTimer = true) {
					const count = this.heroSlides.length
					if (count < 2) return
					this.carouselActiveIndex = (this.carouselActiveIndex + step + count) % count
					if (restartTimer) this.startCarouselTimer()
				},
				nextCarouselSlide() {
					this.advanceCarouselIndex(1)
				},
				previousCarouselSlide() {
					this.advanceCarouselIndex(-1)
				},
				selectCarouselSlide(index) {
					this.carouselActiveIndex = index
					this.startCarouselTimer()
				},
				handleMotionPreference(mediaQuery) {
					this.reducedMotion = mediaQuery.matches
				},
		toggleSubmenu(index) {
			this.showType4 = this.showType4 === index ? -1 : index
		},
		handleGroupFocusOut(event, index) {
			if (this.showType4 === index && !event.currentTarget.contains(event.relatedTarget)) this.showType4 = -1
		},
		handleGroupMouseLeave(event, index) {
			if (this.showType4 === index && !event.currentTarget.contains(document.activeElement)) this.showType4 = -1
		},
		handleOutsideClick(event) {
			if (!event.target.closest('.oc-nav__group')) this.showType4 = -1
			if (this.mobileMenuOpen && !event.target.closest('.oc-header')) this.mobileMenuOpen = false
		},
		closeShellMenus() {
			this.mobileMenuOpen = false
			this.showType4 = -1
		},
		cateClick(url,fenlei){
			this.closeShellMenus()
			this.$router.push(url + '?homeFenlei=' + fenlei);
		},
		preHttp(str) {
			return str && str.substr(0,4)=='http';
		},

			async getSession() {
				const sessionTable = localStorage.getItem('frontSessionTable') || localStorage.getItem('UserTableName')
				await this.$http.get(`${sessionTable}/session`, {emulateJSON: true}).then(async res => {
				if (res.data.code == 0) {
					localStorage.setItem('sessionForm',JSON.stringify(res.data.data))
					localStorage.setItem('frontUserid', res.data.data.accountId || res.data.data.id);
					if(res.data.data.vip) {
						localStorage.setItem('vip', res.data.data.vip);
					}
					if(res.data.data.touxiang) {
						this.headportrait = res.data.data.touxiang
						localStorage.setItem('frontHeadportrait', res.data.data.touxiang);
					} else if(res.data.data.headportrait) {
						this.headportrait = res.data.data.headportrait
						localStorage.setItem('frontHeadportrait', res.data.data.headportrait);
					}
				}
			});
		},
		handleSelect(keyPath) {
			if (keyPath) {
				localStorage.setItem('keyPath', keyPath)
			}
		},
		toLogin() {
		  this.$router.push('/login');
		},
		logout() {
			localStorage.clear();
			Vue.http.headers.common['Token'] = "";
			this.$router.push('/index/home');
			this.activeIndex = '0'
			localStorage.setItem('keyPath', this.activeIndex)
			this.Token = ''
			this.$forceUpdate()
			this.$message({
				message: '登出成功',
				type: 'success',
				duration: 1000,
			});
		},
		getCarousel() {
			this.$http.get('api/v1/banners', {params: { shopId: 1 }}).then(res => {
				if (res.data.code == 0) {
					this.carouselList = res.data.data || [];
				}
			}).catch(() => { this.carouselList = [] });
		},
		// 轮播图跳转
		carouselClick(url) {
			if (url) {
				if (url.indexOf('https') != -1) {
					window.open(url)
				} else {
					this.$router.push(url)
				}
			}
		},
			goBackend() {
			localStorage.setItem('Token', localStorage.getItem('frontToken'));
			localStorage.setItem('role', localStorage.getItem('frontRole'));
			localStorage.setItem('sessionTable', localStorage.getItem('frontSessionTable'));
			localStorage.setItem('headportrait', localStorage.getItem('frontHeadportrait'));
			localStorage.setItem('userid', localStorage.getItem('frontUserid'));
			window.location.href = `${this.$config.baseUrl}admin/dist/index.html`
			
		},
		formatMessages(messages) {
			let lastTime = null;
			messages.forEach((message, index) => {
				const currentTime = new Date(message.addtime).getTime();
				if (lastTime !== null) {
					const timeDiff = (currentTime - lastTime) / 1000 / 60; // 转换为分钟
					if (timeDiff < 3) {
						message.addtime = ''; // 如果小于3分钟，不显示时间
					}
				}
				lastTime = currentTime;
			});
			return messages;
		},
		timeFormat(time) {
			const Time = timeMethod.getTime(time).split("T");
			//当前消息日期属于周
			const week = timeMethod.getDateToWeek(time);
			//当前日期0时
			const nti = timeMethod.setTimeZero(timeMethod.getNowTime());
			//消息日期当天0时
			const mnti = timeMethod.setTimeZero(timeMethod.getTime(time));
			//计算日期差值
			const diffDate = timeMethod.calculateTime(nti, mnti);
			//本周一日期0时 （后面+1是去除当天时间）
			const fwnti = timeMethod.setTimeZero(timeMethod.countDateStr(-timeMethod.getDateToWeek(timeMethod
				.getNowTime()).weekID + 1));
			//计算周日期差值
			const diffWeek = timeMethod.calculateTime(mnti, fwnti);
		
			if (diffDate === 0) { //消息发送日期减去当天日期如果等于0则是当天时间
				return Time[1].slice(0, 5);
			} else if (diffDate < 172800000) { //当前日期减去消息发送日期小于2天（172800000ms）则是昨天-  一天最大差值前天凌晨00:00:00到今天晚上23:59:59
				return "昨天 " + Time[1].slice(0, 5);
			} else if (diffWeek >= 0) { //消息日期减去本周一日期大于0则是本周
				return week.weekName;
			} else { //其他时间则是日期
				return Time[0].slice(5, 10);
			}
		},
		addEmoji(e) {
			this.form.ask += e.native;
			this.showEmoji = false
		},
		getChatList() {
			return this.$http.get('chat/list', {params: { userid: Number(localStorage.getItem('frontUserid')), sort: 'addtime', order: 'asc',limit: 1000 }}).then(res => {
				if (res.data.code == 0) {
					this.chatList = this.formatMessages(res.data.data.list);
					let div = document.getElementsByClassName('chat-content')[0]
					setTimeout(() => {
						if (div){
							div.scrollTop = div.scrollHeight
						}
					}, 0)
				}
			}).catch(() => { this.$message.error('客服消息加载失败，请稍后重试') });
		},
			async addChat(ask=null,type=1) {
				let params = JSON.parse(JSON.stringify(this.form))
				if((!params.ask || !params.ask.trim())&&ask==null){
					this.$message.error('内容不能为空')
					return false
				}
			if(ask){
				params.ask = ask
			}
			params.type = type
			params.uimage = localStorage.getItem('frontHeadportrait')
			params.uname = localStorage.getItem('username')
			params.userid = Number(localStorage.getItem('frontUserid'))
				this.chatSending = true
				try {
					const res = await this.$http.post('chat/add', params)
					if (res.data.code == 0) {
						try { this.websocketSend(ask?ask:params.ask) } catch (error) {}
						this.form.ask = '';
						await this.getChatList();
					} else {
						this.$message.error(res.data.msg || '消息发送失败，请稍后重试')
					}
				} catch (error) {
					this.$message.error('消息发送失败，请检查网络后重试')
				} finally {
					this.chatSending = false
				}
		},
		chatClose() {
			if(this.askType==2){
				this.websocketOnclose();
			}
			this.chatFormVisible = false;
		},
		websocketOnmessage:function(e) {
			this.getChatList()
		},
		goChat() {
			if(!localStorage.getItem('frontToken')) {
				this.toLogin();
				return;
			}
			
			this.initWebSocket(1)
			this.getChatList();
			this.chatFormVisible = true;
		},
		uploadSuccess(res) {
			if (res.code == 0) {
				this.askShow = !this.askShow;
				this.addChat('upload/' + res.file,2)
			}
		},
		uploadSuccess2(res) {
			if (res.code == 0) {
				this.askShow = !this.askShow;
				this.addChat('upload/' + res.file,3)
			}
		},
		uploadSuccess3(res) {
			if (res.code == 0) {
				this.askShow = !this.askShow;
				this.addChat('upload/' + res.file,4)
			}
		},
		download(url){
			if(!url){
				return false
			}
			window.open((location.href.split(this.$config.name).length>1 ? location.href.split(this.$config.name)[0] + this.$config.name + '/' + url :this.$config.baseUrl + url))
		},
			menuShowClick4(index){
				this.showType4 = index
			},
			scrollToRoute(path) {
				const routeView = document.getElementById('scrollView')
				const top = path === '/index/home' ? 0 : (routeView ? routeView.offsetTop : 0)
				window.scrollTo({ top, left: 0, behavior: 'auto' })
			},
			goMenu(path) {
				this.closeShellMenus()
				if (this.$route.path === path) {
					this.scrollToRoute(path)
					return
				}
					if (path === '/index/home') {
						const homeUrl = new URL(window.location.href)
						homeUrl.hash = '#/index/home'
						homeUrl.searchParams.set('__face_home', Date.now().toString())
						window.location.assign(homeUrl.toString())
						return
					}
				this.$router.push(path).then(() => {
					this.$forceUpdate()
					this.$nextTick(() => this.scrollToRoute(path))
				})
			},
		handleCommand(name){
			if(name == 'register') {
				this.logout()
			}
			else if (name == 'service') {
				this.goChat()
			}
			else if (name == 'user'){
				this.goMenu('/index/center')
			}
			else if (name == 'login'){
				this.toLogin()
			}
		},
	}
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	// Legacy generated shell rules are retained only for audit history; the token-driven
	// layout now lives in obsidian-layout.scss and this block is intentionally excluded.
	@if false {
	.top-el-dropdown-menu {
		border: 1px solid #EBEEF5;
		border-radius: 4px;
		padding: 10px 0;
		box-shadow: 0 2px 12px 0 rgba(0,0,0,.1);
		margin: 18px 0;
		background: #fff;
		.service-item {
			border: 0;
			padding: 0 8px;
			margin: 0 0px;
			color: inherit;
			background: #fff;
			width: auto;
			font-size: inherit;
			line-height: 32px;
			height: 32px;
			.icon {
				color: inherit;
				font-size: inherit;
			}
		}
		.service-item:hover {
			color: #333;
			background: #475a8330;
		}
		.user-item {
			border: 0;
			padding: 0 8px;
			margin: 0 0px;
			color: inherit;
			background: #fff;
			width: auto;
			font-size: inherit;
			line-height: 32px;
			height: 32px;
			.icon {
				color: inherit;
				font-size: inherit;
			}
		}
		.user-item:hover {
			color: #333;
			background: #475a8330;
		}
		.register-item {
			border: 0;
			padding: 0 8px;
			margin: 0 0px;
			color: inherit;
			background: #fff;
			width: auto;
			font-size: inherit;
			line-height: 32px;
			height: 32px;
			.icon {
				color: inherit;
				font-size: inherit;
			}
		}
		.register-item:hover {
			color: #333;
			background: #475a8330;
		}
	}
	.main-containers {
		.body-containers {
			padding: 0px 0 0;
			margin: 0;
			background: #fff;
			min-height: 100vh;
			position: relative;
			.top-container {
				padding: 0 calc((100% - 1200px)/2);
				z-index: 1002;
				color: #666;
				display: flex;
				font-size: 14px;
				border-bottom: 0px solid #1f292f;
				box-shadow: 0 0px 0px rgba(64, 158, 255, .3);
				top: 0;
				left: 0;
				background: #08090b;
				width: 100%;
				justify-content: flex-start;
				align-items: flex-start;
				position: inherit;
				height: 130px;
				.top_title {
					top: 60px;
					display: block;
					position: absolute;
					span {
						padding: 0;
						color: rgba(0, 0, 0, 1);
						font-size: 24px;
						line-height: 44px;
						float: left;
					}
				}
				.top_tel {
					margin: 0 10px;
					color: #000;
					font-size: 16px;
				}
				.dropdown-box {
					color: inherit;
					display: flex;
					font-size: inherit;
					position: absolute;
					right: calc((100% - 1200px)/2);
					.el-dropdown-link {
						color: inherit;
						display: flex;
						font-size: inherit;
						align-items: center;
						.top_avatar2 {
							border-radius: 100%;
							margin: 0 10px;
							object-fit: cover;
							display: inline-block;
							width: 28px;
							height: 28px;
						}
						.top_label2 {
							color: inherit;
							font-size: inherit;
							line-height: 32px;
						}
						.top_nickname2 {
							color: inherit;
							font-size: inherit;
							line-height: 32px;
						}
						.icon {
							margin: 0 0 0 5px;
							color: #666;
							font-size: 14px;
						}
						.login-item {
							border: 0;
							padding: 0 8px;
							margin: 0 0px;
							color: inherit;
							background: #fff;
							width: auto;
							font-size: inherit;
							line-height: 32px;
							height: 32px;
							.icon {
								color: inherit;
								font-size: inherit;
							}
						}
						.login-item:hover {
							color: #333;
							background: #475a8330;
						}
					}
				}
			}
			.menu-preview {
				.el-scrollbar {
					height: 100%;
			  
					& ::v-deep .scrollbar-wrapper-vertical {
						overflow-x: hidden;
					}
			  
					& ::v-deep .scrollbar-wrapper-horizontal {
						overflow-y: hidden;
			  
						.el-scrollbar__view {
							white-space: nowrap;
						}
					}
				}
				margin: 0;
				background: #fff;
				width: 100%;
				border-color: #0063CD;
				border-width: 0 0 2px;
				border-style: solid;
				height: 40px;
				.menu-list {
					padding: 0 10px;
					margin: 0 auto;
					background: none;
					display: flex;
					width: 100%;
					justify-content: center;
					position: relative;
					// 首页
					.menu-home {
						cursor: pointer;
						color: #fff;
						.title {
							cursor: pointer;
							padding: 0 20px;
							color: #333;
							display: flex;
							.icon {
								padding: 0 10px;
								margin: 0;
								color: inherit;
								display: none;
								width: 14px;
								font-size: 14px;
								line-height: 38px;
								height: 38px;
							}
							.text {
								padding: 0 10px;
								color: inherit;
								font-size: 16px;
								line-height: 38px;
								height: 38px;
							}
						}
					}
					.menu-home:hover {
						.title {
							color: #0063CD;
						}
					}
					.menu-home.menu-active {
						.title {
							color: #0063CD;
						}
					}
					// 其他盒子
					.menu-item {
						color: #000;
						background: none;
						.title {
							cursor: pointer;
							padding: 0 20px;
							color: #333;
							display: flex;
							span {
								padding: 0 10px;
								margin: 0;
								color: inherit;
								display: none;
								width: 14px;
								font-size: 14px;
								line-height: 38px;
								height: 38px;
							}
							.text {
								padding: 0 10px;
								color: inherit;
								font-size: 16px;
								line-height: 38px;
								height: 38px;
							}
						}
						.menu-child-list {
							z-index: 11;
							flex-direction: column;
							background: rgba(0,102,212,.9);
							display: flex;
							width: 200px;
							justify-content: flex-start;
							position: absolute;
							flex-wrap: wrap;
							.child-item {
								cursor: pointer;
								padding: 0 20px;
								color: #fff;
								width: 100% !important;
								font-size: 15px;
								line-height: 40px;
							}
							.child-item:hover {
								color: #333;
								background: #dde7f2;
							}
						}
					}
					.menu-item:hover {
						.title {
							color: #0063CD;
						}
					}
					.menu-item.menu-active {
						.title {
							color: #0063CD;
						}
					}
					// 客服
					.menu-service {
						cursor: pointer;
						color: #fff;
						display: none;
						line-height: 50px;
						height: 50px;
						.title {
							padding: 0 20px;
							display: flex;
							height: 50px;
							.icon {
								padding: 0 10px;
								margin: 0;
								color: inherit;
								width: 14px;
								font-size: 14px;
								line-height: 50px;
								height: 50px;
							}
							.text {
								padding: 0 10px;
								color: inherit;
								font-size: 14px;
								line-height: 50px;
								height: 50px;
							}
						}
					}
					.menu-service:hover {
						.title {
							background: #000;
						}
					}
					.menu-service.menu-active {
						.title {
							background: #000;
						}
					}
					// 个人中心
					.menu-user {
						cursor: pointer;
						color: #fff;
						display: none;
						.title {
							padding: 0 20px;
							display: flex;
							height: 38px;
							.icon {
								padding: 0 10px;
								margin: 0;
								color: inherit;
								width: 14px;
								font-size: 14px;
								line-height: 60px;
								height: 60px;
							}
							.text {
								padding: 0 10px;
								color: inherit;
								font-size: 16px;
								line-height: 38px;
								height: 38px;
							}
						}
					}
					.menu-user:hover {
						.title {
							color: #0063CD;
						}
					}
					.menu-user.menu-active {
						.title {
							color: #0063CD;
						}
					}
				}
			}
			.banner-preview {
				margin: 0 auto 10px;
				width: 100%;
				height: auto;
				.swiper-button-prev:after {
					display:none;
				}
				.swiper-button-next:after {
					display:none;
				}
				.swiper-slide {
					.swiper-item {
						width: 100%;
						height: auto;
						.el-image {
							object-fit: cover;
							width: 100%;
							height: 400px;
						}
					}
				}
				@keyframes wave1 {from { left: -236px } to { left: -1233px }}
				@keyframes wave2 {from { left: 0 } to { left: -1009px }}
				.swiper-pagination {
					left: 0;
					bottom: 10px;
					width: 100%;
					::v-deep span.swiper-pagination-bullet {
						border-radius: 100%;
						margin: 0 4px;
						background: #000;
						display: inline-block;
						width: 8px;
						opacity: .2;
						height: 8px;
					}
					::v-deep span.swiper-pagination-bullet:hover {
						background: #fff;
						opacity: 1;
					}
					::v-deep span.swiper-pagination-bullet.swiper-pagination-bullet-active {
						background: #fff;
						opacity: 1;
					}
				}
				.swiper-button-next {
					margin: -12px calc((100% - 1200px)/2) 0 0;
					top: 50%;
					width: 24px;
					height: 24px;
					.icon {
						color: #fff;
						width: 24px;
						font-size: 24px;
						height: 24px;
					}
				}
				.swiper-button-prev {
					margin: -12px 0 0 calc((100% - 1200px)/2);
					top: 50%;
					width: 24px;
					height: 24px;
					.icon {
						color: #fff;
						width: 24px;
						font-size: 24px;
						height: 24px;
					}
				}
			}
			.bottom-preview {
				width: 100%;
				height: auto;
				.footer {
					padding: 20px calc((100% - 1200px)/2);
					margin: 0 auto;
					overflow: hidden;
					color: #fff;
					background: #1A82EE;
					width: 100%;
					min-height: 150px;
					text-align: center;
					height: auto;
				}
			}
		}
	}
	.chat-content {
		padding-bottom: 20px;
		width: 100%;
		margin-bottom: 10px;
		max-height: 300px;
		height: 300px;
		overflow-y: scroll;
		border: 1px solid #eeeeee;
		background: #fff;

		.left-content {
			float: left;
			margin-bottom: 10px;
			padding: 10px;
			max-width: 80%;
		}

		.right-content {
			float: right;
			margin-bottom: 10px;
			padding: 10px;
			max-width: 80%;
		}
	}

	.clear-float {
		clear: both;
	}
	.emoji-mart[data-v-7bc71df8] {
		font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif;
		display: -ms-flexbox;
		display: flex;
		-ms-flex-direction: column;
		flex-direction: column;
		height: 420px;
		color: #ffffff !important;
		border: 1px solid #d9d9d9;
		border-radius: 5px;
		background: #fff;
	}
	}
</style>
