<template>
	<div class="home-preview">




			<div id="animate_recommendxinnengyuanqiche" class="recommend">
				<div class="recommend_title_box">
						<span class="recommend_title">护理套餐推荐</span>
					<span class="recommend_description">精选疗程与会员价格，拖动查看全部项目</span>
				</div>
				<circular-gallery :items="packageGalleryItems" :bend="2.15" @select="selectPackage" />
				<div class="moreBtn" @click="moreBtn('xinnengyuanqiche')">
				<span class="text">更多</span>
				<i class="icon iconfont icon-gengduo1"></i>
			</div>
			</div>
			<div id="animate_recommendshouhoufuwu" class="recommend">
				<div class="recommend_title_box">
						<span class="recommend_title">热门美容项目</span>
					<span class="recommend_description">当季高人气护理，点击当前项目查看详情</span>
				</div>
				<circular-gallery :items="popularGalleryItems" :bend="-1.9" @select="selectPopular" />
			<div class="moreBtn" @click="moreBtn('shouhoufuwu')">
				<span class="text">更多</span>
				<i class="icon iconfont icon-gengduo1"></i>
			</div>
			</div>
			<div id="animate_recommendguzhangpaicha" class="recommend">
				<div class="recommend_title_box">
						<span class="recommend_title">专业护理团队</span>
					<span class="recommend_description">认识为你服务的美容师与护理专长</span>
				</div>
				<circular-gallery :items="staffGalleryItems" :bend="2.1" @select="selectStaff" />
			<div class="moreBtn" @click="moreBtn('guzhangpaicha')">
				<span class="text">更多</span>
				<i class="icon iconfont icon-gengduo1"></i>
			</div>
			</div>
	</div>
</template>

<script>
import 'animate.css'
import CircularGallery from '@/components/CircularGallery.vue'

		export default {
			components: { CircularGallery },
		//数据集合
		data() {
			return {
				baseUrl: '',
				newsList: [],
				xinnengyuanqicheRecommend: [],
				shouhoufuwuRecommend: [],
				guzhangpaichaRecommend: [],





			}
		},
		created() {
			this.baseUrl = this.$config.baseUrl;
			this.getList();
		},
		mounted() {
			window.addEventListener('scroll', this.handleScroll)
			setTimeout(()=>{
				this.handleScroll()
			},100)
			
			this.swiperChanges()
		},
			beforeDestroy() {
				window.removeEventListener('scroll', this.handleScroll)
			},
			computed: {
				packageGalleryItems() {
					return this.xinnengyuanqicheRecommend.map(item => ({
						...item,
						image: this.galleryImage(item),
						text: `${item.qichexinghao} · ${item.pinpai} · ¥${item.jiage}`
					}))
				},
				popularGalleryItems() {
					return this.shouhoufuwuRecommend.map(item => ({
						...item,
						image: this.galleryImage(item),
						text: `${item.fuwumingcheng} · ${item.pinpai} · ¥${item.jiage}`
					}))
				},
				staffGalleryItems() {
					return this.guzhangpaichaRecommend.map(item => ({
						...item,
						image: this.galleryImage(item),
						text: item.guzhangmingcheng
					}))
				}
			},
		//方法集合
			methods: {
			swiperChanges() {
				setTimeout(()=>{
				},750)
			},

			listIndexClick11(index, name) {
				this['listIndex11' + name] = index[this['listColumn11' + name]]
				this.getList()
			},

			handleScroll() {
				let arr = [
					{id:'about',css:'animate__'},
					{id:'system',css:'animate__'},
					{id:'animate_recommendxinnengyuanqiche',css:'animate__'},
					{id:'animate_recommendshouhoufuwu',css:'animate__'},
					{id:'animate_recommendguzhangpaicha',css:'animate__'},
				]
			
				for (let i in arr) {
					let doc = document.getElementById(arr[i].id)
					if (doc) {
						let top = doc.offsetTop
						let win_top = window.innerHeight + window.pageYOffset
						// console.log(top,win_top)
						if (win_top > top && doc.classList.value.indexOf(arr[i].css) < 0) {
							// console.log(doc)
							doc.classList.add(arr[i].css)
						}
					}
				}
			},
					preHttp(str) {
						return str && str.substr(0,4)=='http';
					},
					galleryImage(item) {
						if (item.localImage) return item.localImage
						if (item.fengmian && this.preHttp(item.fengmian)) return item.fengmian.split(',')[0]
						if (item.fengmian) return this.baseUrl + item.fengmian.split(',')[0]
						return require('@/assets/images/salon/card-facial.webp')
					},
					serviceImage(index) {
						return [
							require('@/assets/images/salon/card-facial.webp'),
							require('@/assets/images/salon/card-hydration.webp'),
							require('@/assets/images/salon/card-body.webp'),
							require('@/assets/images/salon/path-care.webp'),
							require('@/assets/images/salon/path-packages.webp'),
							require('@/assets/images/salon/path-services.webp')
						][index % 6]
				},
					getList() {
						this.$http.get('api/v1/services', {params: {shopId: 1}}).then(res => {
							if (res.data.code !== 0) return
							const services = (res.data.data || []).map((item, index) => ({
								id: item.id,
								fengmian: item.coverUrl,
								localImage: this.serviceImage(index),
							qichexinghao: item.name,
							pinpai: item.categoryName,
							fuwumingcheng: item.name,
							jiage: item.memberPrice || item.listPrice,
							addtime: '2026-07-22 00:00:00',
							storeupnum: 0,
							clicknum: 0,
							featured: item.featured
						}))
						this.xinnengyuanqicheRecommend = services.filter(item => item.pinpai === '护理套餐').slice(0, 6)
						if (!this.xinnengyuanqicheRecommend.length) this.xinnengyuanqicheRecommend = services.slice(0, 3)
						this.shouhoufuwuRecommend = services.filter(item => item.featured).slice(0, 6)
					})
					this.$http.get('api/v1/staff', {params: {shopId: 1}}).then(res => {
						if (res.data.code !== 0) return
							this.guzhangpaichaRecommend = (res.data.data || []).map((item, index) => ({
								id: item.id,
								fengmian: item.avatarUrl,
								localImage: item.avatarUrl ? null : require('@/assets/images/salon/staff-avatar.webp'),
							guzhangmingcheng: `${item.name} · ${item.levelName || item.jobRole}`,
							addtime: '2026-07-22 00:00:00',
							storeupnum: 0,
							clicknum: 0
						})).slice(0, 6)
					})
				},
				toDetail(path, item) {
				this.$router.push({path: '/index/' + path, query: {id: item.id}});
				},
				selectPackage(item) {
					this.toDetail('xinnengyuanqicheDetail', item)
				},
				selectPopular(item) {
					this.toDetail('shouhoufuwuDetail', item)
				},
				selectStaff(item) {
					this.toDetail('guzhangpaichaDetail', item)
				},
			moreBtn(path) {
				this.$router.push({path: '/index/' + path});
			}
		}
	}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
		.home-preview {
			margin: 0px auto;
			flex-direction: column;
			background: transparent;
			display: flex;
			width: 100%;
			.recommend {
				padding: 48px 0 56px;
				margin: 0;
				background: transparent;
				border-top: 1px solid var(--oc-border);
				border-radius: 0;
			width: 100%;
			position: relative;
				.recommend_title_box {
					padding: 0px;
						margin: 0 auto 22px;
					background: none;
						display: flex;
						align-items: baseline;
						gap: 16px;
					width: 1200px;
				position: relative;
				text-align: left;
				.recommend_title {
					margin: 0;
					color: var(--oc-text);
					background: none;
					width: auto;
						font-size: 26px;
						line-height: 38px;
						letter-spacing: -.02em;
				}
					.recommend_subhead {
					margin: 0;
					color: #999;
					display: none;
					width: auto;
					font-size: 18px;
					line-height: 40px;
					text-align: center;
				}
			}
			.index-pv1 .animation-box {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				z-index: initial;
			}
			
			.index-pv1 .animation-box:hover {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				-webkit-perspective: 1000px;
				perspective: 1000px;
				transition: 0s;
				z-index: 1;
			}
			
			.index-pv1 .animation-box img {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
			}
			
			.index-pv1 .animation-box img:hover {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				-webkit-perspective: 1000px;
				perspective: 1000px;
				transition: 0s;
			}
				.list1 {
					padding: 0;
					margin: 0 auto;
					background: transparent;
					display: grid;
					grid-template-columns: repeat(3, minmax(0, 1fr));
					gap: 18px;
					width: 1200px;
					border: 0;
				height: auto;
				.list-item {
					cursor: pointer;
					padding: 10px;
					margin: 0;
					color: var(--oc-text-muted);
						display: block;
					font-size: 14px;
					border-color: var(--oc-border);
						background: rgba(37, 27, 37, .78);
						transition: transform 220ms cubic-bezier(.22,1,.36,1), border-color 220ms ease, background-color 220ms ease;
						width: 100%;
						border-width: 1px;
						border-radius: var(--oc-radius-md);
					position: relative;
					border-style: solid;
					height: auto;
						img {
						margin: 0 0 5px;
						object-fit: cover;
						display: block;
						width: 100%;
						height: 180px;
					}
					.name {
						padding: 0 10px;
						overflow: hidden;
						color: var(--oc-text);
						white-space: nowrap;
						font-weight: 600;
						width: 100%;
						font-size: 14px;
						line-height: 30px;
						text-overflow: ellipsis;
					}
					.price {
						padding: 0 10px;
						color: #f00;
						font-size: 14px;
						line-height: 1.5;
					}
					.time_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: inherit;
							display: none;
							font-size: inherit;
							line-height: 1.5;
						}
						.service-media {
							margin: 0 0 5px;
							display: flex;
							width: 100%;
							height: 240px;
							border-radius: var(--oc-radius-sm);
							align-items: flex-end;
							padding: 20px;
							color: var(--oc-text);
							font-size: 18px;
							font-weight: 600;
							background: radial-gradient(circle at 70% 20%, rgba(196,147,125,.28), transparent 34%), linear-gradient(145deg, var(--oc-surface-3), var(--oc-surface-1));
						}
						.label {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
						.text {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
					}
					.publisher_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: inherit;
							display: none;
							font-size: inherit;
							line-height: 1.5;
						}
						.label {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
						.text {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
					}
					.like_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: inherit;
							display: none;
							font-size: inherit;
							line-height: 1.5;
						}
						.label {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
						.text {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
					}
					.collect_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: inherit;
							display: none;
							font-size: inherit;
							line-height: 1.5;
						}
						.label {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
						.text {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
					}
					.view_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: inherit;
							display: none;
							font-size: inherit;
							line-height: 1.5;
						}
						.label {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
						.text {
							color: inherit;
							font-size: inherit;
							line-height: 1.5;
						}
					}
					.recommend_description {
						color: var(--oc-text-muted);
						font-size: 14px;
						line-height: 22px;
					}
				}
					.list-item:hover { transform: translateY(-4px); border-color: rgba(215, 161, 140, .62); background: var(--oc-surface-3); }
				.list-item:hover img { transform: scale(1.025); }
				.list-item img { transition: transform 240ms cubic-bezier(.22,1,.36,1); }
			}
			.moreBtn {
				border: 0px solid #999;
				cursor: pointer;
				padding: 0;
				margin: 0;
				display: inline-block;
				line-height: 32px;
				right: calc((100% - 1200px)/2);
				float: right;
				top: 5px;
				background: none;
				width: auto;
				position: absolute;
				text-align: right;
				transition: transform 180ms cubic-bezier(.22,1,.36,1), color 180ms ease;
				.text {
					color: #999;
					font-size: 14px;
				}
				.icon {
					color: #999;
					font-size: 14px;
			}
		}
		@media (max-width: 1260px) {
			.home-preview .recommend .recommend_title_box,
			.home-preview .recommend .list1 { width: 100%; }
			.home-preview .recommend { padding-inline: 12px; }
			.home-preview .recommend .moreBtn { right: 12px; }
		}
			@media (max-width: 760px) {
				.home-preview .recommend { padding-top: 36px; }
				.home-preview .recommend .recommend_title_box { align-items: flex-start; flex-direction: column; gap: 4px; }
			.home-preview .recommend .list1 { grid-template-columns: 1fr; }
			.home-preview .recommend .list1 .list-item img { height: 220px; }
		}
			.moreBtn:hover { transform: translateX(4px); }
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.home-preview .list-item, .home-preview .list-item img, .home-preview .moreBtn { transition: none !important; transform: none !important; }
	}
</style>
