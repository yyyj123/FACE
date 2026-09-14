<template>
	<div class="addEdit-block oc-form-section">
		<el-form
			class="add-update-preview oc-admin-form"
			ref="ruleForm"
			:model="ruleForm"
			:rules="rules"
			label-width="180px"
		>
			<template >
				<el-form-item class="input" v-if="type!='info'" label="服务编号" prop="weixiubianhao" >
					<el-input v-model="ruleForm.weixiubianhao" placeholder="服务编号" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-else-if="ruleForm.weixiubianhao" label="服务编号" prop="weixiubianhao" >
					<el-input v-model="ruleForm.weixiubianhao" placeholder="服务编号" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="项目名称" prop="fuwumingcheng" >
					<el-input v-model="ruleForm.fuwumingcheng" placeholder="项目名称" clearable  :readonly="ro.fuwumingcheng"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="项目名称" prop="fuwumingcheng" >
					<el-input v-model="ruleForm.fuwumingcheng" placeholder="项目名称" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="项目分类" prop="fuwufenlei" >
					<el-input v-model="ruleForm.fuwufenlei" placeholder="项目分类" clearable  :readonly="ro.fuwufenlei"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="项目分类" prop="fuwufenlei" >
					<el-input v-model="ruleForm.fuwufenlei" placeholder="项目分类" readonly></el-input>
				</el-form-item>
				<el-form-item class="upload" v-if="type!='info' && !ro.fengmian" label="封面" prop="fengmian" >
					<file-upload
						tip="点击上传封面"
						action="file/upload"
						:limit="3"
						:multiple="true"
						:fileUrls="ruleForm.fengmian?ruleForm.fengmian:''"
						@change="fengmianUploadChange"
					></file-upload>
				</el-form-item>
				<el-form-item class="upload" v-else-if="ruleForm.fengmian" label="封面" prop="fengmian" >
					<img v-if="ruleForm.fengmian.substring(0,4)=='http'" class="upload-img" style="margin-right:20px;" v-bind:key="index" :src="ruleForm.fengmian.split(',')[0]" :alt="`${ruleForm.fuwumingcheng || '服务记录'}封面`" width="100" height="100">
					<img v-else class="upload-img" style="margin-right:20px;" v-bind:key="index" v-for="(item,index) in ruleForm.fengmian.split(',')" :src="$base.url+item" :alt="`${ruleForm.fuwumingcheng || '服务记录'}封面 ${index + 1}`" width="100" height="100">
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="价格" prop="jiage" >
					<el-input-number v-model="ruleForm.jiage" placeholder="价格" :disabled="ro.jiage"></el-input-number>
				</el-form-item>
				<el-form-item v-else class="input" label="价格" prop="jiage" >
					<el-input v-model="ruleForm.jiage" placeholder="价格" readonly></el-input>
				</el-form-item>
				<el-form-item class="select" v-if="type!='info'" label="产品名称" prop="peijianmingcheng" >
					<el-select multiple filterable :disabled="ro.peijianmingcheng" @change="peijianmingchengChange" v-model="ruleForm.peijianmingcheng" placeholder="请选择产品名称">
						<el-option
							v-for="(item,index) in peijianmingchengOptions"
							v-bind:key="index"
							:label="item"
							:value="item">
						</el-option>
					</el-select>
				</el-form-item>
				<el-form-item class="input" v-else-if="ruleForm.peijianmingcheng" label="产品名称" prop="peijianmingcheng" >
					<el-input v-model="ruleForm.peijianmingcheng" placeholder="产品名称" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="产品售价" prop="allshoujia" >
					<el-input-number v-model="ruleForm.allshoujia" placeholder="产品售价" :disabled="ro.allshoujia"></el-input-number>
				</el-form-item>
				<el-form-item v-else class="input" label="产品售价" prop="allshoujia" >
					<el-input v-model="ruleForm.allshoujia" placeholder="产品售价" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'" label="总价" prop="zongjia" >
					<el-input v-model="zongjia" placeholder="总价" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-else-if="ruleForm.zongjia" label="总价" prop="zongjia" >
					<el-input v-model="ruleForm.zongjia" placeholder="总价" readonly></el-input>
				</el-form-item>
				<el-form-item class="date" v-if="type!='info'" label="服务时间" prop="weixiushijian" >
					<el-date-picker
						value-format="yyyy-MM-dd HH:mm:ss"
						v-model="ruleForm.weixiushijian" 
						type="datetime"
						:readonly="ro.weixiushijian"
						placeholder="服务时间"
					></el-date-picker>
				</el-form-item>
				<el-form-item class="input" v-else-if="ruleForm.weixiushijian" label="服务时间" prop="weixiushijian" >
					<el-input v-model="ruleForm.weixiushijian" placeholder="服务时间" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="账号" prop="zhanghao" >
					<el-input v-model="ruleForm.zhanghao" placeholder="账号" clearable  :readonly="ro.zhanghao"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="账号" prop="zhanghao" >
					<el-input v-model="ruleForm.zhanghao" placeholder="账号" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="姓名" prop="xingming" >
					<el-input v-model="ruleForm.xingming" placeholder="姓名" clearable  :readonly="ro.xingming"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="姓名" prop="xingming" >
					<el-input v-model="ruleForm.xingming" placeholder="姓名" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="手机" prop="shouji" >
					<el-input v-model="ruleForm.shouji" placeholder="手机" clearable  :readonly="ro.shouji"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="手机" prop="shouji" >
					<el-input v-model="ruleForm.shouji" placeholder="手机" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="到店备注" prop="chepaihao" >
					<el-input v-model="ruleForm.chepaihao" placeholder="到店备注" clearable  :readonly="ro.chepaihao"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="到店备注" prop="chepaihao" >
					<el-input v-model="ruleForm.chepaihao" placeholder="到店备注" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="美容师账号" prop="weixiuzhanghao" >
					<el-input v-model="ruleForm.weixiuzhanghao" placeholder="美容师账号" clearable  :readonly="ro.weixiuzhanghao"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="美容师账号" prop="weixiuzhanghao" >
					<el-input v-model="ruleForm.weixiuzhanghao" placeholder="美容师账号" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="美容师姓名" prop="weixiuxingming" >
					<el-input v-model="ruleForm.weixiuxingming" placeholder="美容师姓名" clearable  :readonly="ro.weixiuxingming"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="美容师姓名" prop="weixiuxingming" >
					<el-input v-model="ruleForm.weixiuxingming" placeholder="美容师姓名" readonly></el-input>
				</el-form-item>
			</template>
			<el-form-item v-if="type!='info'"  label="服务说明" prop="weixiushuoming" >
				<editor 
					style="min-width: 200px; max-width: 600px;"
					v-model="ruleForm.weixiushuoming" 
					class="editor" 
					action="file/upload">
				</editor>
			</el-form-item>
			<el-form-item v-else-if="ruleForm.weixiushuoming" label="服务说明" prop="weixiushuoming" >
				<span class="text" v-html="ruleForm.weixiushuoming"></span>
			</el-form-item>
			<el-form-item class="btn">
				<el-button class="btn3"  v-if="type!='info'" type="success" @click="onSubmit">
					<span class="icon iconfont icon-xihuan"></span>
					提交
				</el-button>
				<el-button class="btn4" v-if="type!='info'" type="success" @click="back()">
					<span class="icon iconfont icon-xihuan"></span>
					取消
				</el-button>
				<el-button class="btn5" v-if="type=='info'" type="success" @click="back()">
					<span class="icon iconfont icon-xihuan"></span>
					返回
				</el-button>
			</el-form-item>
		</el-form>
    

	</div>
</template>
<script>
	import { 
		isNumber,
	} from "@/utils/validate";
	export default {
		data() {
			var validateNumber = (rule, value, callback) => {
				if(!value){
					callback();
				} else if (!isNumber(value)) {
					callback(new Error("请输入数字"));
				} else {
					callback();
				}
			};
			return {
				id: '',
				type: '',
			
			
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
						{ validator: validateNumber, trigger: 'blur' },
					],
					peijianmingcheng: [
					],
					allshoujia: [
						{ validator: validateNumber, trigger: 'blur' },
					],
					zongjia: [
						{ validator: validateNumber, trigger: 'blur' },
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
			};
		},
		props: ["parent"],
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
			this.ruleForm.weixiushijian = this.getCurDateTime()
		},
		methods: {
			// 下载
			download(file){
				window.open(`${file}`)
			},
			// 初始化
			init(id,type) {
				if (id) {
					this.id = id;
					this.type = type;
				}
				if(this.type=='info'||this.type=='else'){
					this.info(id);
				}else if(this.type=='logistics'){
					this.logistics=false;
					this.info(id);
				}else if(this.type=='cross'){
					var obj = this.$storage.getObj('crossObj');
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
							this.ruleForm.fengmian = obj[o];
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
				}
				// 获取用户信息
				this.$http({
					url: `${this.$storage.get('sessionTable')}/session`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						var json = data.data;
						if(((json.zhanghao!=''&&json.zhanghao) || json.zhanghao==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.zhanghao = json.zhanghao
							this.ro.zhanghao = true;
						}
						if(((json.xingming!=''&&json.xingming) || json.xingming==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.xingming = json.xingming
							this.ro.xingming = true;
						}
						if(((json.shouji!=''&&json.shouji) || json.shouji==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.shouji = json.shouji
							this.ro.shouji = true;
						}
						if(((json.chepaihao!=''&&json.chepaihao) || json.chepaihao==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.chepaihao = json.chepaihao
							this.ro.chepaihao = true;
						}
						if(((json.weixiuzhanghao!=''&&json.weixiuzhanghao) || json.weixiuzhanghao==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.weixiuzhanghao = json.weixiuzhanghao
							this.ro.weixiuzhanghao = true;
						}
						if(((json.weixiuxingming!=''&&json.weixiuxingming) || json.weixiuxingming==0) && this.$storage.get("role")!="管理员"){
							this.ruleForm.weixiuxingming = json.weixiuxingming
							this.ro.weixiuxingming = true;
						}
					} else {
						this.$message.error(data.msg);
					}
				});
				this.$http({
					url: `option/peijianxinxi/peijianmingcheng`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						this.peijianmingchengOptions = data.data;
					} else {
						this.$message.error(data.msg);
					}
				});
			
			},
			// 下多随
			peijianmingchengChange (columnValue) {
				let allshoujia = 0
				for(let x in columnValue){
					this.$http({
						url: `follow/peijianxinxi/peijianmingcheng?columnValue=`+ columnValue[x],
						method: "get"
					}).then(({ data }) => {
						if (data && data.code === 0) {
							if(data.data.shoujia){
								allshoujia += Number(data.data.shoujia)
							}
							this.ruleForm.allshoujia = Number((allshoujia).toFixed(2))
						} else {
							this.$message.error(data.msg);
						}
					});	
				}
			},
			// 多级联动参数

			info(id) {
				this.$http({
					url: `weixiujilu/info/${id}`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						this.ruleForm = data.data;
						//解决前台上传图片后台不显示的问题
						let reg=new RegExp('../../../upload','g')//g代表全部
						this.ruleForm.peijianmingcheng = this.ruleForm.peijianmingcheng.split(",");
						this.ruleForm.weixiushuoming = this.ruleForm.weixiushuoming.replace(reg,'../../../car/upload');
					} else {
						this.$message.error(data.msg);
					}
				});
			},

			// 提交
			async onSubmit() {
					this.ruleForm.ispay = '未支付'
					if(this.ruleForm.weixiubianhao) {
						this.ruleForm.weixiubianhao = String(this.ruleForm.weixiubianhao)
					}
					this.ruleForm.zongjia = this.zongjia
					if(this.ruleForm.fengmian!=null) {
						this.ruleForm.fengmian = this.ruleForm.fengmian.replace(new RegExp(this.$base.url,"g"),"");
					}
					var objcross = this.$storage.getObj('crossObj');
					await this.$refs["ruleForm"].validate(async valid => {
						if (valid) {
							if(this.type=='cross'){
								var statusColumnName = this.$storage.get('statusColumnName');
								var statusColumnValue = this.$storage.get('statusColumnValue');
								if(statusColumnName!='') {
									var obj = this.$storage.getObj('crossObj');
									if(statusColumnName && !statusColumnName.startsWith("[")) {
										for (var o in obj){
											if(o==statusColumnName){
												obj[o] = statusColumnValue;
											}
										}
										var table = this.$storage.get('crossTable');
										await this.$http({
											url: `${table}/update`,
											method: "post",
											data: obj
										}).then(({ data }) => {});
									}
								}
							}
							this.ruleForm.peijianmingcheng = this.ruleForm.peijianmingcheng.join(",");
							
							await this.$http({
								url: `weixiujilu/${!this.ruleForm.id ? "save" : "update"}`,
								method: "post",
								data: this.ruleForm
							}).then(async ({ data }) => {
								if (data && data.code === 0) {
									this.$message({
										message: "操作成功",
										type: "success",
										duration: 1500,
										onClose: () => {
											if(this.isAuth('weixiujilu','支付')&&this.type=='cross') {
												this.$confirm('是否跳转支付？').then(_ => {
													this.parent.showFlag = true;
													this.parent.addOrUpdateFlag = false;
													this.parent.weixiujiluCrossAddOrUpdateFlag = false;
													this.$router.push('/weixiujilu')
												}).catch(_ => {
													this.parent.showFlag = true;
													this.parent.addOrUpdateFlag = false;
													this.parent.weixiujiluCrossAddOrUpdateFlag = false;
													this.parent.search();
													this.parent.contentStyleChange();
												});
											}else {
												this.parent.showFlag = true;
												this.parent.addOrUpdateFlag = false;
												this.parent.weixiujiluCrossAddOrUpdateFlag = false;
												this.parent.search();
												this.parent.contentStyleChange();
											}
											
										}
									});
								} else {
									this.$message.error(data.msg);
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
				this.parent.showFlag = true;
				this.parent.addOrUpdateFlag = false;
				this.parent.weixiujiluCrossAddOrUpdateFlag = false;
				this.parent.contentStyleChange();
			},
			fengmianUploadChange(fileUrls) {
				this.ruleForm.fengmian = fileUrls;
			},
		}
	};
</script>
<style lang="scss" scoped>
	.addEdit-block {
		padding: 30px;
	}
	.add-update-preview {
		border-radius: 12px;
		padding: 30px;
		background: rgba(255,255,255,0);
		border-color: #ddd;
		border-width: 1px;
		border-style: solid;
	}
	.amap-wrapper {
		width: 100%;
		height: 500px;
	}
	
	.search-box {
		position: absolute;
	}
	
	.el-date-editor.el-input {
		width: auto;
	}
	.add-update-preview ::v-deep .el-form-item {
		border: 0px solid #eee;
		padding: 2px 20px;
		margin: 0 0 20px 0;
		display: inline-block;
		width: 48%;
	}
	.add-update-preview .el-form-item ::v-deep .el-form-item__label {
		padding: 0 10px 0 0;
		color: #666;
		font-weight: 500;
		width: 180px;
		font-size: 16px;
		line-height: 40px;
		text-align: right;
	}
	
	.add-update-preview .el-form-item ::v-deep .el-form-item__content {
		margin-left: 180px;
	}
	.add-update-preview .el-form-item span.text {
		color: #666;
		font-weight: 500;
		display: inline-block;
		font-size: 16px;
		line-height: 40px;
	}
	
	.add-update-preview .el-input {
		width: 100%;
	}
	.add-update-preview .el-input ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input ::v-deep .el-input__inner[readonly="readonly"] {
		border: 0px solid #ccc;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number {
		text-align: left;
		width: 100%;
	}
	.add-update-preview .el-input-number ::v-deep .el-input__inner {
		text-align: left;
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number ::v-deep .is-disabled .el-input__inner {
		text-align: left;
		border: 0px solid #ccc;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number ::v-deep .el-input-number__decrease {
		display: none;
	}
	.add-update-preview .el-input-number ::v-deep .el-input-number__increase {
		display: none;
	}
	.add-update-preview .el-select {
		width: auto;
	}
	.add-update-preview .el-select ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 10px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-select ::v-deep .is-disabled .el-input__inner {
		border: 0;
		cursor: not-allowed;
		border-radius: 4px;
		padding: 0 10px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-date-editor {
		width: auto;
	}
	.add-update-preview .el-date-editor ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 10px 0 30px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-date-editor ::v-deep .el-input__inner[readonly="readonly"] {
		border: 0;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 10px 0 30px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .viewBtn {
		border: 1px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		padding: 0 15px;
		margin: 0 20px 0 0;
		color: #666;
		background: linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(238,238,238,1) 100%);
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #666;
			font-size: 14px;
			height: 34px;
		}
	}
	.add-update-preview .viewBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview .downBtn {
		border: 1px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		padding: 0 15px;
		margin: 0 20px 0 0;
		color: #666;
		background: linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(238,238,238,1) 100%);
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #666;
			font-size: 16px;
			height: 34px;
		}
	}
	.add-update-preview .downBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview .unBtn {
		border: 0;
		cursor: not-allowed;
		border-radius: 4px;
		padding: 0 0px;
		margin: 0 20px 0 0;
		outline: none;
		color: #999;
		background: none;
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #fff;
			display: none;
			font-size: 14px;
			height: 34px;
		}
	}
	.add-update-preview .unBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview ::v-deep .el-upload--picture-card {
		background: transparent;
		border: 0;
		border-radius: 0;
		width: auto;
		height: auto;
		line-height: initial;
		vertical-align: middle;
	}
	
	.add-update-preview ::v-deep .upload .upload-img {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	
	.add-update-preview ::v-deep .el-upload-list .el-upload-list__item {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	
	.add-update-preview ::v-deep .el-upload .el-icon-plus {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	.add-update-preview ::v-deep .el-upload__tip {
		color: #838fa1;
		font-size: 16px;
	}
	
	.add-update-preview .el-textarea ::v-deep .el-textarea__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 12px;
		color: #666;
		background: #fff;
		width: auto;
		font-size: 16px;
		min-width: 400px;
		height: 120px;
	}
	.add-update-preview .el-textarea ::v-deep .el-textarea__inner[readonly="readonly"] {
				border: 0;
				cursor: not-allowed;
				border-radius: 0px;
				padding: 12px;
				color: #666;
				background: none;
				width: auto;
				font-size: 16px;
				min-width: 400px;
				height: auto;
			}
	.add-update-preview .el-form-item.btn {
		padding: 0;
		margin: 20px 0 0;
		.btn1 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn1:hover {
			opacity: 0.8;
		}
		.btn2 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 34px;
			}
		}
		.btn2:hover {
			opacity: 0.8;
		}
		.btn3 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn3:hover {
			opacity: 0.8;
		}
		.btn4 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn4:hover {
			opacity: 0.8;
		}
		.btn5 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn5:hover {
			opacity: 0.8;
		}
	}
</style>
