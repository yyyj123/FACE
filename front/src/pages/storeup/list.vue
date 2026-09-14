<template>
		<div class="storeup-preview" :class="{'center-embedded-module': embedded}">
			<div v-if="!embedded" class="back_box">
				<el-button class="backBtn" size="mini" @click="backClick">
				<span class="icon iconfont icon-jiantou33"></span>
				<span class="text">返回</span>
			</el-button>
		</div>
			<div v-if="storeupType==1 && !embedded" class="section-title">我的收藏</div>
			<el-form :inline="true" :model="formSearch" class="formSearch storeup-toolbar">
				<el-form-item>
					<el-input v-model="formSearch.name" placeholder="名称"></el-input>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" @click="getStoreupList(1)">查询</el-button>
			</el-form-item>
		</el-form>
			<div v-if="storeupList.length" class="storeup-grid">
					<div class="storeup-item" v-for="item in storeupList" :key="item.id" @click="toDetail(item)">
						<el-card class="storeup-card" :body-style="{ padding: '0px', cursor: 'pointer' }">
						<img :src="resolveStoreupImage(item)" class="image" :alt="`${item.name || '收藏内容'}封面`" @error="storeupImageError($event, item)">
						<div class="storeup-card__body">
							<span>{{item.name}}</span>
						</div>
					</el-card>
				</div>
			</div>
			<div v-else class="oc-empty storeup-empty">暂无收藏内容</div>
	
		<el-pagination
			background
			id="pagination" class="pagination"
			:pager-count="7"
			:page-size="pageSize"
			:page-sizes="pageSizes"
			prev-text="上一页"
			next-text="下一页"
			:hide-on-single-page="false"
			:layout='["total","prev","pager","next","sizes","jumper"].join()'
			:total="total"
				@current-change="curChange"
			@prev-click="prevClick"
			@size-change="sizeChange"
			@next-click="nextClick"
			></el-pagination>
	
	</div>
</template>

<script>
	import config from '@/config/config'
	export default {
		props: {
			embedded: {
				type: Boolean,
				default: false,
			},
		},
		data() {
			return {
				layouts: '',
				baseUrl: config.baseUrl,
				formSearch: {
					name: ''
				},
				storeupType: 1,
				storeupList: [],
				total: 1,
				pageSize: 8,
				pageSizes: [],
				totalPage: 1
			}
			},
			created() {
				this.storeupType = Number(localStorage.getItem('storeupType') || 1);
				this.getStoreupList(1);
			},
			methods: {
				storeupFallback(item) {
					const fallbacks = {
						shouhoufuwu: [
							require('@/assets/images/salon/card-hydration.webp'),
							require('@/assets/images/salon/card-facial.webp'),
							require('@/assets/images/salon/card-body.webp')
						],
						xinnengyuanqiche: [require('@/assets/images/salon/path-packages.webp')],
						guzhangpaicha: [require('@/assets/images/salon/path-care.webp')]
					};
					const options = fallbacks[item.tablename] || [require('@/assets/images/salon/card-facial.webp')];
					return options[Math.abs(Number(item.refid) || 0) % options.length];
				},
				resolveStoreupImage(item) {
					const picture = String(item.picture || '').split(',')[0].trim();
					if (!picture) return this.storeupFallback(item);
					if (/^(https?:|data:|blob:)/.test(picture)) return picture;
					if (picture.startsWith('/')) return picture;
					return this.baseUrl + picture;
				},
					storeupImageError(event, item) {
						if (event.target.dataset.fallbackApplied === '1') return;
						event.target.dataset.fallbackApplied = '1';
						event.target.src = this.storeupFallback(item);
					},
				backClick() {
				this.$router.push('/index/center')
			},
			getStoreupList(page) {
					let params = {page, limit: this.pageSize, type: this.storeupType, userid: Number(localStorage.getItem('frontUserid')),sort:"addtime",order:"desc"};
				let searchWhere = {
				};
				if (this.formSearch.name != '') searchWhere.name = '%' + this.formSearch.name + '%';
				this.$http.get('storeup/list', {params: Object.assign(params, searchWhere)}).then(res => {
					if (res.data.code == 0) {
							this.storeupList = (res.data.data.list || []).filter(item => item.refid && item.tablename && item.name);
						this.total = res.data.data.total;
						this.pageSize = Number(res.data.data.pageSize);
						this.totalPage = res.data.data.totalPage;
						if(this.pageSizes.length==0){
							this.pageSizes = [this.pageSize, this.pageSize*2, this.pageSize*3, this.pageSize*5];
						}
					}
				});
			},
			curChange(page) {
				this.getStoreupList(page);
			},
			prevClick(page) {
				this.getStoreupList(page);
			},
			sizeChange(size){
				this.pageSize = size
				this.getStoreupList(1);
			},
			nextClick(page) {
				this.getStoreupList(page);
			},
			toDetail(item) {
				this.$router.push({path: `/index/${item.tablename}Detail`, query: {id:item.refid}});
			}
		}
	}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.storeup-preview {
		box-sizing: border-box;
		width: min(1200px, 100%);
		margin: 0 auto;
		padding: 0 0 20px;
		color: var(--oc-text);
		background: transparent;
		font-size: 15px;
		position: relative;

		.section-title {
			box-sizing: border-box;
			width: 100%;
			margin: 0 0 18px;
			padding: 0 0 12px;
			border-bottom: 1px solid var(--oc-border);
			color: var(--oc-text);
			font-size: 22px;
			font-weight: 700;
			line-height: 1.4;
		}

		.storeup-toolbar.formSearch {
			box-sizing: border-box;
			display: flex;
			align-items: center;
			gap: 10px;
			width: 100%;
			margin: 0 0 18px;
			padding: 14px 16px;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-1);
			text-align: left;

			::v-deep .el-form-item {
				margin: 0;
			}

			::v-deep .el-form-item:first-child {
				flex: 1 1 280px;
				min-width: 0;
			}

			::v-deep .el-form-item__content,
			::v-deep .el-input {
				width: 100%;
			}

			::v-deep .el-input__inner {
				box-sizing: border-box;
				width: 100%;
				height: 50px;
				border: 1px solid var(--oc-border);
				border-radius: var(--oc-radius-sm);
				background: var(--oc-surface-2);
				color: var(--oc-text);
			}
		}

			.storeup-grid {
				display: grid;
				grid-template-columns: repeat(auto-fill, minmax(240px, 300px));
				gap: 16px;
				justify-content: start;
				width: 100%;
			}

		.storeup-item {
			min-width: 0;
		}

		.storeup-card.el-card {
			overflow: hidden;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-1);
			color: var(--oc-text);
			box-shadow: none;
			transition: border-color .2s ease, transform .2s ease;
		}

		.storeup-card.el-card:hover {
			border-color: rgba(200, 111, 138, .72);
			transform: translateY(-2px);
		}

			.image {
				display: block;
				width: 100%;
				height: 190px;
				background: var(--oc-surface-2);
				object-fit: cover;
			}

			.storeup-card__body {
				display: flex;
				align-items: center;
				min-height: 58px;
				padding: 14px 16px;
				color: var(--oc-text);
				font-weight: 650;
				line-height: 1.45;
			}

		.storeup-empty {
			min-height: 180px;
		}

		.pagination.el-pagination {
			box-sizing: border-box;
			display: flex;
			align-items: center;
			justify-content: center;
			gap: 6px;
			width: 100%;
			margin: 18px 0 0;
			padding: 12px;
			overflow-x: auto;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-1);
			color: var(--oc-text-muted);
			white-space: nowrap;
		}

		::v-deep .pagination .btn-prev,
		::v-deep .pagination .btn-next,
		::v-deep .pagination .el-pager li {
			border: 1px solid var(--oc-border) !important;
			border-radius: var(--oc-radius-sm) !important;
			background: var(--oc-surface-2) !important;
			color: var(--oc-text-muted) !important;
		}

		::v-deep .pagination .btn-prev:disabled,
		::v-deep .pagination .btn-next:disabled {
			background: rgba(255, 255, 255, .025) !important;
			color: rgba(244, 236, 240, .38) !important;
		}

		::v-deep .pagination .el-pager li.active {
			border-color: rgba(200, 111, 138, .72) !important;
			background: rgba(200, 111, 138, .2) !important;
			color: var(--oc-text) !important;
		}
	}

		@media (max-width: 640px) {
			.storeup-preview .storeup-grid {
				grid-template-columns: minmax(0, 1fr);
			}

			.storeup-preview .storeup-toolbar.formSearch {
			align-items: stretch;
			flex-direction: column;
		}

		.storeup-preview .storeup-grid {
			grid-template-columns: minmax(0, 1fr);
		}

		.storeup-preview .pagination.el-pagination {
			justify-content: flex-start;
		}
	}
		
</style>
