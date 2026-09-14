<template>
	<div>
		<div class="breadcrumb-preview">
			<el-breadcrumb :separator="'/'">
				<el-breadcrumb-item class="item1" to="/"><a>首页</a></el-breadcrumb-item>
				<el-breadcrumb-item class="item2" v-for="(item, index) in breadcrumbItem" :key="index"><a>{{item.name}}</a></el-breadcrumb-item>
			</el-breadcrumb>
		</div>
		<div v-if="centerType" class="back_box">
			<el-button class="backBtn" size="mini" @click="backClick">
				<span class="icon iconfont icon-jiantou33"></span>
				<span class="text">返回</span>
			</el-button>
		</div>
		<div class="list-preview">
			<div class="category-3">
				<div class="item" :class="swiperIndex == '-1' ? 'active' : ''" @click="getList(1, '全部')" :plain="isPlain">
					<div class="text">全部</div>
				</div>
				<div class="item" :class="swiperIndex == index ? 'active' : ''" v-for="(item, index) in fenlei" :key="index" @click="getList(1, item[feileiColumn], 'btn' + index)" :ref="'btn' + index" plain>
					<img v-if="item.image" :src="baseUrl + (item.image?item.image.split(',')[0]:'')">
					<div class="text">{{item[feileiColumn]}}</div>
				</div>
			</div>
			<el-form :inline="true" :model="formSearch" class="list-form-pv">
				<el-form-item class="list-item">
					<div class="lable">项目名称：</div>
					<el-input v-model="formSearch.fuwumingcheng" placeholder="项目名称" @keydown.enter.native="getList(1, curFenlei)" clearable></el-input>
				</el-form-item>
				<el-button class="list-search-btn" v-if=" true " type="primary" @click="getList(1, curFenlei)">
					查询
				</el-button>
				<el-button class="list-add-btn" v-if="btnAuth('shouhoufuwu','新增')" type="primary" @click="add('/index/shouhoufuwuAdd')">
					添加
				</el-button>
			</el-form>
			<div class="select2">
					<div class="select2-list" v-for="(item,index) in selectOptionsList" :key="item.tableName || index">
					<div class="label">{{item.name}}：</div>
					<div class="item-body">
						<div class="item" @click="selectClick2(item,-1)" :class="item.check ==-1 ? 'active' : ''">全部</div>
							<div class="item" @click="selectClick2(item,index1)" :class="item.check == index1 ? 'active' : ''" v-for="(item1,index1) in item.list" :key="String(item1)">{{item1}}</div>
					</div>
				</div>
				</div>
				<div class="sort_view">
					<button type="button" class="sort-option" :class="{'is-active': sortType=='clicknum'}" :aria-pressed="String(sortType=='clicknum')" @click="sortClick('clicknum')">
						点击最多
					</button>
					<button type="button" class="sort-option" :class="{'is-active': sortType=='storeupnum'}" :aria-pressed="String(sortType=='storeupnum')" @click="sortClick('storeupnum')">
						收藏最多
					</button>
				</div>
			<div class="list">
				<!-- 样式一 -->
				<div class="list1 index-pv1">
					<div v-for="(item, index) in dataList" :key="index" @click.stop="toDetail(item)" class="list-item animation-box">
							<img class="image" :src="serviceImage(item)" @click.stop="toDetail(item)" :alt="`${item.fuwumingcheng}项目图片，点击查看详情`" />
						<div class="name">{{item.fuwumingcheng}}</div>
						<div class="time_item">
							<span class="icon iconfont icon-shijian21"></span>
							<span class="label">上架时间：</span>
							<span class="text">{{item.addtime}}</span>
						</div>
						<div class="collect_item">
							<span class="icon iconfont icon-shoucang10"></span>
							<span class="label">收藏量：</span>
							<span class="text">{{item.storeupnum}}</span>
						</div>
						<div class="view_item" v-if="item.clicknum">
							<span class="icon iconfont icon-chakan9"></span>
							<span class="label">点击量：</span>
							<span class="text">{{item.clicknum}}</span>
						</div>
					</div>
				</div>
			</div>

	
			<el-pagination
				background
				id="pagination"
				class="pagination"
				:pager-count="7"
				:page-size="pageSize"
				prev-text="上一页"
				next-text="下一页"
				:hide-on-single-page="false"
				:layout='["total","prev","pager","next","sizes","jumper"].join()'
				:total="total"
				:page-sizes="pageSizes"
				@current-change="curChange"
				@size-change="sizeChange"
				@prev-click="prevClick"
				@next-click="nextClick"
				></el-pagination>
			<div class="idea1"></div>
		</div>
		</div>
</template>
<script>
	export default {
		//数据集合
		data() {
			return {
				selectIndex2: 0,
				selectOptionsList: [],
				layouts: '',
				swiperIndex: -1,
				baseUrl: '',
				breadcrumbItem: [
					{
						name: '美容项目'
					}
				],
				formSearch: {
					fuwumingcheng: '',
					fuwufenlei: '',
				},
				fenlei: [],
				feileiColumn: '',
				dataList: [],
				total: 1,
				pageSize: 20,
				pageSizes: [],
				totalPage: 1,
				curFenlei: '全部',
				isPlain: false,
				indexQueryCondition: '',
				fuwufenleiOptions: [],
				timeRange: [],
				centerType:false,
					sortType: 'id',
				sortOrder: 'desc',
			}
		},
		async created() {
			if(this.$route.query.centerType&&this.$route.query.centerType!=0){
				this.centerType = true
			}
			this.baseUrl = this.$config.baseUrl;
			await this.getFenlei();
			this.fuwufenleiOptions = this.fenlei.map(item => item.fuwufenlei)
			this.selectOptionsList.push({name:'项目分类',list:this.fuwufenleiOptions,tableName: 'fuwufenlei',check: -1})
			let fenlei = '全部'
			if(this.$route.query.homeFenlei){
				fenlei = this.$route.query.homeFenlei
			}
			this.getList(1, fenlei);
		},
		watch:{
			$route(newValue){
				this.getList(1, newValue.query.homeFenlei);
			}
		},
		//方法集合
		methods: {
			serviceImage(item) {
				if (item.fengmian) {
					const image = item.fengmian.split(',')[0]
					return image.startsWith('http') ? image : this.baseUrl + image
				}
				const images = [
					require('@/assets/images/salon/card-hydration.webp'),
					require('@/assets/images/salon/card-facial.webp'),
					require('@/assets/images/salon/card-body.webp')
				]
				return images[Math.abs(Number(item.id) || 0) % images.length]
			},
			selectClick2(row,index) {
				row.check = index
				if(index == -1){
					this.formSearch[row.tableName] = ''
				}else {
					this.formSearch[row.tableName] = row.list[index]
				}
				this.getList()
			},
			add(path) {
				let query = {}
				if(this.centerType){
					query.centerType = 1
				}
				this.$router.push({path: path,query:query});
			},
			async getFenlei() {
				await this.$http.get('api/v1/service-categories',{params: {shopId: 1}}).then(res => {
					if (res.data.code == 0) {
						this.fenlei = (res.data.data || []).map(item => ({
							id: item.id,
							fuwufenlei: item.name
						}))
					}
				}).catch(() => { this.fenlei = [] });
				this.feileiColumn = 'fuwufenlei'
			},
			getList(page, fenlei, ref = '') {
				if(fenlei == '全部') this.swiperIndex = -1;
				for(let i=0;i<this.fenlei.length;i++) {
					if(fenlei == this.fenlei[i][this.feileiColumn]) {
						this.swiperIndex = i;
						break;
					}
				}
				if(fenlei){
					this.curFenlei = fenlei;
				}
				const selectedCategory = this.formSearch.fuwufenlei || (this.curFenlei !== '全部' ? this.curFenlei : '')
				const category = this.fenlei.find(item => item.fuwufenlei === selectedCategory)
					const params = { shopId: 1 }
					if (category) params.categoryId = category.id
					if (this.sortType === 'clicknum' || this.sortType === 'storeupnum') params.sort = this.sortType
					this.$http.get('api/v1/services', {params}).then(res => {
					if (res.data.code == 0) {
						const keyword = this.formSearch.fuwumingcheng.trim().toLowerCase()
						let rows = (res.data.data || []).map(item => ({
							...item,
							fuwumingcheng: item.name,
							fuwufenlei: item.categoryName,
							fengmian: item.coverUrl,
							jiage: item.listPrice,
								clicknum: Number(item.clicknum) || 0,
								storeupnum: Number(item.storeupnum) || 0,
							addtime: ''
						}))
						if (keyword) rows = rows.filter(item => item.fuwumingcheng.toLowerCase().includes(keyword))
						this.total = rows.length
						this.totalPage = Math.max(1, Math.ceil(this.total / this.pageSize))
						const currentPage = Number(page) || 1
						this.dataList = rows.slice((currentPage - 1) * this.pageSize, currentPage * this.pageSize)
						if(this.pageSizes.length==0){
							this.pageSizes = [this.pageSize, this.pageSize*2, this.pageSize*3, this.pageSize*5];
						}
					}
				}).catch(() => {
					this.dataList = []
					this.total = 0
				});
			},
				sortClick(type){
						this.sortType = type
						this.sortOrder = 'desc'
						this.getList(1, this.curFenlei)
					},
			curChange(page) {
				this.getList(page,this.curFenlei);
			},
			prevClick(page) {
				this.getList(page,this.curFenlei);
			},
			sizeChange(size){
				this.pageSize = size
				this.getList(1,this.curFenlei);
			},
			nextClick(page) {
				this.getList(page,this.curFenlei);
			},
				toDetail(item) {
				let params = {
					id: item.id
				}
				if(this.centerType){
					params.centerType = 1
				}
				this.$router.push({path: '/index/shouhoufuwuDetail', query: params});
			},
			btnAuth(tableName,key){
				if(this.centerType){
					return this.isBackAuth(tableName,key)
				}else{
					return this.isAuth(tableName,key)
				}
			},
			backClick() {
				this.$router.push({path: '/index/center'});
			},
		}
	}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.list-preview {
		margin: 0px auto;
		color: #333;
		background: none;
		display: flex;
		width: 1200px;
		font-size: 16px;
		justify-content: flex-start;
		align-items: flex-start;
		position: relative;
		flex-wrap: wrap;
		.category-3 {
			padding: 0px;
			margin: 20px 0px 0 0;
			background: #fff;
			display: flex;
			width: 100%;
			justify-content: center;
			flex-wrap: wrap;
			height: auto;
			order: 6;
			.item {
				cursor: pointer;
				border: 0px solid #475a8350;
				padding: 8px 20px 8px 26px;
				margin: 0 10px 20px 0;
				color: inherit;
				background: #fff;
				display: flex;
				font-size: 16px;
				justify-content: center;
				align-items: center;
				flex-wrap: wrap;
				min-width: 110px;
				img {
					border-radius: 100%;
					margin: 0 5px 0 0;
					object-fit: cover;
					display: block;
					width: 40px;
					height: 40px;
				}
				.text {
					color: inherit;
					font-size: inherit;
				}
			}
			.item:hover {
				border-radius: 4px;
				color: #fff;
				background: #0066D4;
			}
			.item.active {
				border-radius: 4px;
				color: #fff;
				background: #0066D4;
				font-size: 16px;
			}
		}
		.list-form-pv {
			padding: 0;
			margin: 20px 0;
			color: inherit;
			background: none;
			display: flex;
			width: 100%;
			font-size: inherit;
			flex-wrap: wrap;
			height: auto;
			.list-item {
				padding: 0;
				margin: 0 0px 10px 0;
				display: flex;
				font-size: inherit;
				align-items: center;
				flex-wrap: wrap;
				::v-deep.el-form-item__content {
					display: flex;
				}
				.lable {
					padding: 0 10px;
					color: #333;
					white-space: nowrap;
					display: inline-block;
					width: auto;
					font-size: 16px;
					line-height: 36px;
				}
				.el-input {
					width: auto;
				}
				.datetimerange {
					border: 1px solid #0066D450 !important;
					border-radius: 8px;
					padding: 3px 3px;
					background: #fff;
					width: auto;
					justify-content: center;
				}
				.el-input ::v-deep .el-input__inner {
					border: 1px solid #0066D450;
					border-radius: 4px;
					padding: 0 10px;
					margin: 0 5px 0 0;
					color: #333;
					width: auto;
					font-size: 16px;
					line-height: 36px;
					height: 36px;
				}
				.el-select {
					width: 100%;
				}
				.el-select ::v-deep .el-input__inner {
				}
				.el-date-editor {
					width: auto;
				}
				.el-date-editor ::v-deep .el-input__inner {
					border: 1px solid #0066D450;
					border-radius: 4px;
					padding: 0 0px 0 30px;
					margin: 0;
					color: #333;
					width: auto;
					font-size: 16px;
					line-height: 36px;
					height: 36px;
				}
			}
			.list-search-btn {
				cursor: pointer;
				border: 0;
				border-radius: 4px;
				padding: 0px 15px;
				margin: 0 10px 0 10px;
				color: #fff;
				background: #0066D4;
				width: auto;
				font-size: inherit;
				line-height: 36px;
				height: 36px;
				i {
					margin: 0 10px 0 0;
					color: #fff;
					font-size: inherit;
				}
			}
			.list-add-btn {
				cursor: pointer;
				border: 1px solid #0066D4;
				border-radius: 4px;
				padding: 0px 15px;
				margin: 0 10px 0 0;
				color: #0066D4;
				background: #fff;
				width: auto;
				font-size: inherit;
				line-height: 36px;
				height: 36px;
				i {
					margin: 0 10px 0 0;
					color: #fff;
					font-size: inherit;
				}
			}
		}
		.select2 {
			padding: 10px 0;
			margin: 10px auto 0;
			background: none;
			width: 100%;
			font-size: 15px;
			height: auto;
			.select2-list {
				padding: 5px 5px;
				margin: 0 0 10px;
				background: none;
				width: 100%;
				height: auto;
				.label {
					padding: 0 5px;
					color: #333;
					font-weight: 500;
					display: inline-block;
					font-size: inherit;
					line-height: 32px;
				}
				.item-body {
					display: inline-block;
					width: auto;
					flex-wrap: wrap;
					height: auto;
					.item {
						border-radius: 4px;
						padding: 0 5px;
						color: inherit;
						background: none;
						display: inline-block;
						font-size: inherit;
						line-height: 32px;
						text-align: center;
						min-width: 50px;
					}
					.item:hover {
						cursor: pointer;
						color: #333;
						background: #0066D430;
					}
					.item.active {
						cursor: pointer;
						color: #333;
						background: #0066D430;
						display: inline-block;
						min-width: 50px;
						text-align: center;
					}
				}
			}
		}
		.sort_view {
			padding: 5px 20px;
			margin: 0px auto 20px;
			color: #333;
			background: #fff;
			width: 100%;
			font-size: inherit;
			border-color: #0066D450;
			border-width: 1px 0 4px;
			border-style: solid;
			order: 3;
		}
		.list {
			margin: 20px 0;
			overflow: hidden;
			background: #fff;
			width: calc(100% - 0px);
			clear: both;
			font-size: 15px;
			order: 8;
			.index-pv1 .animation-box {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				z-index: initial;
			}
				
			.index-pv1 .animation-box:hover {
				transform: rotate(0) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				-webkit-perspective: 1000px;
				perspective: 1000px;
				transition: 0.3s;
				z-index: 1;
			}
				
			.index-pv1 .animation-box img {
				transform: rotate(0deg) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
			}
			
			.index-pv1 .animation-box img:hover {
				transform: rotate(0) scale(1) skew(0deg, 0deg) translate3d(0px, 0px, 0px);
				-webkit-perspective: 1000px;
				perspective: 1000px;
				transition: 0.3s;
			}
			.list1 {
				padding: 0;
				align-content: flex-start;
				background: #fff;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 1px 0 0 1px;
				justify-content: flex-start;
				align-items: flex-start;
				border-style: solid;
				flex-wrap: wrap;
				height: auto;
				.list-item {
					cursor: pointer;
					padding: 10px;
					margin: 0;
					background: #fff;
					display: flex;
					width: 20%;
					border-color: #ddd;
					border-width: 0 1px 1px 0;
					position: relative;
					border-style: solid;
					flex-wrap: wrap;
					height: auto;
					.image {
						margin: 5px 0;
						object-fit: cover;
						display: block;
						width: 100%;
						height: 160px;
						order: -1;
					}
					.price {
						padding: 0 10px;
						color: #f00;
						font-size: 14px;
						line-height: 1.5;
					}
					.name {
						padding: 0 10px;
						overflow: hidden;
						color: #333;
						white-space: nowrap;
						width: 100%;
						font-size: 15px;
						line-height: 30px;
						text-overflow: ellipsis;
						text-align: center;
						order: -1;
					}
					.time_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.label {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.text {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
					}
					.publisher_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.label {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.text {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
					}
					.like_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.label {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.text {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
					}
					.collect_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.label {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.text {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
					}
					.view_item {
						padding: 0 10px;
						display: none;
						.icon {
							margin: 0 2px 0 0;
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.label {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
						.text {
							color: #666;
							font-size: 12px;
							line-height: 1.5;
						}
					}
				}
			}
		}
		.idea1 {
			background: #fff;
			width: 100%;
			order: 6;
			height: 1px;
		}
	}
</style>
