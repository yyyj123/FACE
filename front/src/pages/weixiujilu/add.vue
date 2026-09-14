<template>
	<div class="add-update-preview">
		<el-form
			class="add-update-form"
			ref="ruleForm"
			:model="ruleForm"
			:rules="rules"
			label-width="180px"
			>
			<el-form-item class="add-item" label="服务编号" prop="weixiubianhao">
				<el-input v-model="ruleForm.weixiubianhao" placeholder="服务编号" disabled></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="项目名称" prop="fuwumingcheng">
				<el-input v-model="ruleForm.fuwumingcheng" 
					placeholder="项目名称" clearable :disabled=" false  ||ro.fuwumingcheng"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="项目分类" prop="fuwufenlei">
				<el-input v-model="ruleForm.fuwufenlei" 
					placeholder="项目分类" clearable :disabled=" false  ||ro.fuwufenlei"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="封面" v-if="type!='cross' || (type=='cross' && !ro.fengmian)" prop="fengmian">
				<file-upload
					tip="点击上传封面"
					action="file/upload"
					:limit="3"
					:multiple="true"
					:fileUrls="ruleForm.fengmian?ruleForm.fengmian:''"
					@change="fengmianUploadChange"
					></file-upload>
			</el-form-item>
			<el-form-item class="add-item" v-else label="封面" prop="fengmian">
				<img v-if="ruleForm.fengmian.substring(0,4)=='http'" class="upload-img" v-bind:key="index" :src="ruleForm.fengmian.split(',')[0]">
				<img v-else class="upload-img" v-bind:key="index" v-for="(item,index) in ruleForm.fengmian.split(',')" :src="baseUrl+item">
			</el-form-item>
			<el-form-item class="add-item" label="价格" prop="jiage">
				<el-input-number v-model="ruleForm.jiage" placeholder="价格" :disabled=" false ||ro.jiage"></el-input-number>
			</el-form-item>
			<el-form-item class="add-item" label="产品名称" prop="peijianmingcheng">
				<el-select multiple @change="peijianmingchengChange" v-model="ruleForm.peijianmingcheng" placeholder="请选择产品名称" filterable>
					<el-option
						v-for="(item,index) in peijianmingchengOptions"
						:key="index"
						:label="item"
						:value="item">
					</el-option>
				</el-select>
			</el-form-item>
			<el-form-item class="add-item" label="产品售价" prop="allshoujia">
				<el-input-number v-model="ruleForm.allshoujia" placeholder="产品售价" :disabled=" false ||ro.allshoujia"></el-input-number>
			</el-form-item>
			<el-form-item class="add-item" label="总价" prop="zongjia">
				<el-input v-model="zongjia" placeholder="总价" disabled></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="服务时间" prop="weixiushijian">
				<el-date-picker
					:disabled=" false  ||ro.weixiushijian"
					value-format="yyyy-MM-dd HH:mm:ss"
					v-model="ruleForm.weixiushijian" 
					type="datetime"
					placeholder="服务时间">
				</el-date-picker>
			</el-form-item>
			<el-form-item class="add-item" label="账号" prop="zhanghao">
				<el-input v-model="ruleForm.zhanghao" 
					placeholder="账号" clearable :disabled=" false  ||ro.zhanghao"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="姓名" prop="xingming">
				<el-input v-model="ruleForm.xingming" 
					placeholder="姓名" clearable :disabled=" false  ||ro.xingming"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="手机" prop="shouji">
				<el-input v-model="ruleForm.shouji" 
					placeholder="手机" clearable :disabled=" false  ||ro.shouji"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="到店备注" prop="chepaihao">
				<el-input v-model="ruleForm.chepaihao" 
					placeholder="到店备注" clearable :disabled=" false  ||ro.chepaihao"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="美容师账号" prop="weixiuzhanghao">
				<el-input v-model="ruleForm.weixiuzhanghao" 
					placeholder="美容师账号" clearable :disabled=" false  ||ro.weixiuzhanghao"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="美容师姓名" prop="weixiuxingming">
				<el-input v-model="ruleForm.weixiuxingming" 
					placeholder="美容师姓名" clearable :disabled=" false  ||ro.weixiuxingming"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="服务说明" prop="weixiushuoming">
				<editor 
					v-model="ruleForm.weixiushuoming" 
					class="editor" 
					action="file/upload">
				</editor>
			</el-form-item>

			<el-form-item class="add-btn-item">
				<el-button class="submitBtn"  type="primary" @click="onSubmit">
					<span class="icon iconfont "></span>
					<span class="text">提交</span>
				</el-button>
				<el-button class="closeBtn" @click="back()">
					<span class="icon iconfont "></span>
					<span class="text">取消</span>
				</el-button>
			</el-form-item>
		</el-form>
	</div>
</template>

<script>
	export default {
		data() {
			return {
				id: '',
				baseUrl: '',
				ro:{
					weixiubianhao : false,
					fuwumingcheng : false,
					fuwufenlei : false,
					fengmian : false,
					jiage : false,
					peijianmingcheng : false,
					allshoujia : false,
					zongjia : false,
					weixiushijian : false,
					weixiushuoming : false,
					zhanghao : false,
					xingming : false,
					shouji : false,
					chepaihao : false,
					weixiuzhanghao : false,
					weixiuxingming : false,
					ispay : false,
				},
				type: '',
				userTableName: localStorage.getItem('UserTableName'),
				ruleForm: {
					weixiubianhao: this.getUUID(),
					fuwumingcheng: '',
					fuwufenlei: '',
					fengmian: '',
					jiage: '',
					peijianmingcheng: '',
					allshoujia: '',
					zongjia: '',
					weixiushijian: '',
					weixiushuoming: '',
					zhanghao: '',
					xingming: '',
					shouji: '',
					chepaihao: '',
					weixiuzhanghao: '',
					weixiuxingming: '',
				},
				peijianmingchengOptions: [],


				rules: {
					weixiubianhao: [
					],
					fuwumingcheng: [
					],
					fuwufenlei: [
					],
					fengmian: [
					],
					jiage: [
						{ validator: this.$validate.isNumber, trigger: 'blur' },
					],
					peijianmingcheng: [
					],
					allshoujia: [
						{ validator: this.$validate.isNumber, trigger: 'blur' },
					],
					zongjia: [
						{ validator: this.$validate.isNumber, trigger: 'blur' },
					],
					weixiushijian: [
					],
					weixiushuoming: [
					],
					zhanghao: [
					],
					xingming: [
					],
					shouji: [
					],
					chepaihao: [
					],
					weixiuzhanghao: [
					],
					weixiuxingming: [
					],
					ispay: [
					],
				},
				centerType: false,
			};
		},
		computed: {
			zongjia: {
				get: function () {
					return 0+parseFloat(this.ruleForm.jiage==""?0:this.ruleForm.jiage)+parseFloat(this.ruleForm.allshoujia==""?0:this.ruleForm.allshoujia) || 0
				}
			},



		},
		components: {
		},
		created() {
			if(this.$route.query.centerType){
				this.centerType = true
			}
			//this.bg();
			let type = this.$route.query.type ? this.$route.query.type : '';
			this.init(type);
			this.baseUrl = this.$config.baseUrl;
			this.ruleForm.weixiushijian = this.getCurDateTime()
		},
		methods: {
			getMakeZero(s) {
				return s < 10 ? '0' + s : s;
			},
			// 下载
			download(file){
				window.open(`${file}`)
			},
			// 初始化
			init(type) {
				this.type = type;
				if(type=='cross'){
					var obj = JSON.parse(localStorage.getItem('crossObj'));
					for (var o in obj){
						if(o=='weixiubianhao'){
							this.ruleForm.weixiubianhao = obj[o];
							this.ro.weixiubianhao = true;
							continue;
						}
						if(o=='fuwumingcheng'){
							this.ruleForm.fuwumingcheng = obj[o];
							this.ro.fuwumingcheng = true;
							continue;
						}
						if(o=='fuwufenlei'){
							this.ruleForm.fuwufenlei = obj[o];
							this.ro.fuwufenlei = true;
							continue;
						}
						if(o=='fengmian'){
							this.ruleForm.fengmian = obj[o].split(",")[0];
							this.ro.fengmian = true;
							continue;
						}
						if(o=='jiage'){
							this.ruleForm.jiage = obj[o];
							this.ro.jiage = true;
							continue;
						}
						if(o=='peijianmingcheng'){
							this.ruleForm.peijianmingcheng = obj[o];
							this.ro.peijianmingcheng = true;
							continue;
						}
						if(o=='allshoujia'){
							this.ruleForm.allshoujia = obj[o];
							this.ro.allshoujia = true;
							continue;
						}
						if(o=='zongjia'){
							this.ruleForm.zongjia = obj[o];
							this.ro.zongjia = true;
							continue;
						}
						if(o=='weixiushijian'){
							this.ruleForm.weixiushijian = obj[o];
							this.ro.weixiushijian = true;
							continue;
						}
						if(o=='weixiushuoming'){
							this.ruleForm.weixiushuoming = obj[o];
							this.ro.weixiushuoming = true;
							continue;
						}
						if(o=='zhanghao'){
							this.ruleForm.zhanghao = obj[o];
							this.ro.zhanghao = true;
							continue;
						}
						if(o=='xingming'){
							this.ruleForm.xingming = obj[o];
							this.ro.xingming = true;
							continue;
						}
						if(o=='shouji'){
							this.ruleForm.shouji = obj[o];
							this.ro.shouji = true;
							continue;
						}
						if(o=='chepaihao'){
							this.ruleForm.chepaihao = obj[o];
							this.ro.chepaihao = true;
							continue;
						}
						if(o=='weixiuzhanghao'){
							this.ruleForm.weixiuzhanghao = obj[o];
							this.ro.weixiuzhanghao = true;
							continue;
						}
						if(o=='weixiuxingming'){
							this.ruleForm.weixiuxingming = obj[o];
							this.ro.weixiuxingming = true;
							continue;
						}
					}
				}else if(type=='edit'){
					this.info()
				}
				// 获取用户信息
				this.$http.get(this.userTableName + '/session', {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						var json = res.data.data;
						if((json.zhanghao!=''&&json.zhanghao) || json.zhanghao==0){
							this.ruleForm.zhanghao = json.zhanghao;
							this.ro.zhanghao = true;
						}
						if((json.xingming!=''&&json.xingming) || json.xingming==0){
							this.ruleForm.xingming = json.xingming;
							this.ro.xingming = true;
						}
						if((json.shouji!=''&&json.shouji) || json.shouji==0){
							this.ruleForm.shouji = json.shouji;
							this.ro.shouji = true;
						}
						if((json.chepaihao!=''&&json.chepaihao) || json.chepaihao==0){
							this.ruleForm.chepaihao = json.chepaihao;
							this.ro.chepaihao = true;
						}
						if((json.weixiuzhanghao!=''&&json.weixiuzhanghao) || json.weixiuzhanghao==0){
							this.ruleForm.weixiuzhanghao = json.weixiuzhanghao;
							this.ro.weixiuzhanghao = true;
						}
						if((json.weixiuxingming!=''&&json.weixiuxingming) || json.weixiuxingming==0){
							this.ruleForm.weixiuxingming = json.weixiuxingming;
							this.ro.weixiuxingming = true;
						}
					}
				});
				this.$http.get('option/peijianxinxi/peijianmingcheng', {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						this.peijianmingchengOptions = res.data.data;
					}
				});

				if (localStorage.getItem('raffleType') && localStorage.getItem('raffleType') != null) {
					localStorage.removeItem('raffleType')
					setTimeout(() => {
						this.onSubmit()
					}, 300)
				}
			},
			// 下多随
			peijianmingchengChange (columnValue) {
				let allshoujia = 0
				for(let x in columnValue){
					this.$http.get('follow/peijianxinxi/peijianmingcheng?columnValue=' + columnValue[x], {emulateJSON: true}).then(res => {
						if (res.data.code == 0) {
							if(res.data.data.shoujia){
								allshoujia += Number(res.data.data.shoujia)
							}
							this.ruleForm.allshoujia = allshoujia
						}
					})
				}
			},

			// 多级联动参数
			// 多级联动参数
			info() {
				this.$http.get(`weixiujilu/detail/${this.$route.query.id}`, {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						this.ruleForm = res.data.data;
						this.ruleForm.peijianmingcheng = this.ruleForm.peijianmingcheng.split(",");
					}
				});
			},
			// 提交
			async onSubmit() {
				if(this.ruleForm.weixiubianhao){
					this.ruleForm.weixiubianhao = String(this.ruleForm.weixiubianhao)
				}
				this.ruleForm.zongjia = this.zongjia
				await this.$refs["ruleForm"].validate(async valid => {
					if(valid) {
						if(this.type=='cross'){
							var statusColumnName = localStorage.getItem('statusColumnName');
							var statusColumnValue = localStorage.getItem('statusColumnValue');
							if(statusColumnName && statusColumnName!='') {
								var obj = JSON.parse(localStorage.getItem('crossObj'));
								if(!statusColumnName.startsWith("[")) {
									for (var o in obj){
										if(o==statusColumnName){
											obj[o] = statusColumnValue;
										}
									}
									var table = localStorage.getItem('crossTable');
									await this.$http.post(table+'/update', obj).then(res => {});
								}
							}
						}


						this.ruleForm.peijianmingcheng = this.ruleForm.peijianmingcheng.join(",");
						await this.$http.post(`weixiujilu/${this.ruleForm.id?'update':this.centerType?'save':'add'}`, this.ruleForm).then(async res => {
							if (res.data.code == 0) {
								this.$message({
									message: '操作成功',
									type: 'success',
									duration: 1500,
									onClose: () => {
										if(this.isBackAuth('weixiujilu','支付')&&this.type=='cross'){
											this.$confirm('是否跳转支付？') .then(_ => {
												let jumpParams = {
													id: res.data.data,
													centerType: 1
												}
												this.$router.push({path: '/index/weixiujiluDetail', query: jumpParams});
											}).catch(_ => {
												this.$router.go(-1);
											});
										}else {
											this.$router.go(-1);
										}
										
										
									}
								});
							} else {
								this.$message({
									message: res.data.msg,
									type: 'error',
									duration: 1500
								});
							}
						});
					}
				});
			},
			// 获取uuid
			getUUID () {
				return new Date().getTime();
			},
			// 返回
			back() {
				this.$router.go(-1);
			},
			fengmianUploadChange(fileUrls) {
				this.ruleForm.fengmian = fileUrls.replace(new RegExp(this.$config.baseUrl,"g"),"");
			},
		}
	};
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.add-update-preview {
		padding: 0 0 20px;
		margin: 0px auto;
		color: #666;
		background: #fff;
		width: 1200px;
		font-size: 16px;
		position: relative;
		.add-update-form {
			margin: 20px 0 0;
			width: 100%;
			position: relative;
			.add-item.el-form-item {
				border-radius: 0px;
				padding: 6px 0 0;
				margin: 0 0 20px 0;
				background: none;
				border-color: #475a8310;
				border-width:  0 0 0px;
				border-style: solid;
				::v-deep .el-form-item__label {
					padding: 0 10px 0 0;
					color: #666;
					font-weight: 500;
					width: 180px;
					font-size: inherit;
					line-height: 40px;
					text-align: right;
				}
				::v-deep .el-form-item__content {
					margin-left: 180px;
				}
				.el-input {
					width: auto;
				}
				.el-input ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input ::v-deep .el-input__inner[readonly="readonly"] {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: rgba(85, 85, 127, 1.0);
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .el-input__inner {
					text-align: left;
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .is-disabled .el-input__inner {
					text-align: left;
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: rgba(85, 85, 127, 1.0);
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .el-input-number__decrease {
					display: none;
				}
				.el-input-number ::v-deep .el-input-number__increase {
					display: none;
				}
				.el-select {
					width: auto;
				}
				.el-select ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 10px;
					color: inherit;
					width: 100%;
					font-size: 16px;
					min-width: inherit !important;
					height: 40px;
				}
				.el-select ::v-deep .is-disabled .el-input__inner {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 10px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: 100%;
					font-size: 16px;
					height: 40px;
				}
				.el-date-editor {
					width: auto;
				}
				.el-date-editor ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 10px 0 30px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-date-editor ::v-deep .el-input__inner[readonly="readonly"] {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 10px 0 30px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				::v-deep .el-upload--picture-card {
					background: transparent;
					border: 0;
					border-radius: 0;
					width: auto;
					height: auto;
					line-height: initial;
					vertical-align: middle;
				}
				::v-deep .upload .upload-img {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
				}
				::v-deep .el-upload-list .el-upload-list__item {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
					font-size: 14px;
					line-height: 1.8;
				}
				::v-deep .el-upload .el-icon-plus {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
				}
				::v-deep .el-upload__tip {
					color: #888;
					font-size: 16px;
				}
				.el-textarea ::v-deep .el-textarea__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					min-height: 150px;
					min-width: 48%;
					height: auto;
				}
				.el-textarea ::v-deep .el-textarea__inner[readonly="readonly"] {
					border: 0px solid #ddd;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 12px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: auto;
					font-size: 16px;
					min-height: 150px;
					min-width: 50%;
					height: auto;
				}
				::v-deep .el-input__inner::placeholder {
					color: inherit;
					font-size: inherit;
				}
				::v-deep textarea::placeholder {
					color: inherit;
					font-size: inherit;
				}
				.editor {
					background-color: #fff;
					border-radius: 0;
					padding: 0;
					box-shadow: none;
					margin: 0;
					width: 100%;
					min-height: 350px;
					border-color: #ccc;
					border-width: 1px;
					border-style: solid;
					height: auto;
				}
				.upload-img {
					object-fit: cover;
					width: 100px;
					height: 100px;
				}
				.viewBtn {
					border: 0;
					cursor: pointer;
					border-radius: 4px;
					padding: 0 20px;
					margin: 0;
					color: #333;
					background: #475a8330;
					display: inline-block;
					width: auto;
					font-size: 14px;
					line-height: 34px;
					height: 34px;
				}
				.viewBtn:hover {
				}
				.unviewBtn {
					border: 0;
					cursor: pointer;
					padding: 0 20px;
					margin: 0;
					color: #333;
					display: inline-block;
					font-size: 14px;
					line-height: 34px;
					border-radius: 4px;
					outline: none;
					background: #ddd;
					width: auto;
					height: 34px;
				}
				.unviewBtn:hover {
				}
			}
			.add-btn-item {
				padding: 0;
				margin: 20px 0;
				.submitBtn {
					border: 0;
					cursor: pointer;
					padding: 0 24px 0 30px;
					margin: 0 20px 0 0;
					display: inline-block;
					font-size: 16px;
					line-height: 44px;
					border-radius: 4px;
					background: #0066D4;
					width: auto;
					text-align: center;
					min-width: 120px;
					height: 44px;
					.icon {
						color: #fff;
					}
					.text {
						color: #fff;
					}
				}
				.submitBtn:hover {
					color: #fff;
					.icon {
					}
					.text {
					}
				}
				.closeBtn {
					border: 1px solid #0066D450;
					cursor: pointer;
					padding: 0 24px 0 30px;
					margin: 0 20px 0 0;
					color: #0066D4;
					display: inline-block;
					font-size: 16px;
					line-height: 44px;
					border-radius: 4px;
					background: #fff;
					width: auto;
					text-align: center;
					min-width: 120px;
					height: 44px;
					.icon {
						color: #333;
					}
					.text {
						color: #0066D4;
					}
				}
				.closeBtn:hover {
					color: #80593c;
					.icon {
					}
					.text {
					}
				}
			}
		}
	}
	.el-date-editor.el-input {
		width: auto;
	}
</style>
